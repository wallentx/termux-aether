"""Collector regressions without a device or wall-clock delays."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('capture', Path(__file__).with_name('capture.py'))
capture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(capture)


class CaptureTest(unittest.TestCase):
    def run_capture(self, metadata, empty_frames=False, delayed_start=False):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        output = Path(directory.name) / 'report'
        states = ('ready', 'running', 'done') if delayed_start else ('ready', 'done')
        phases = iter((name, state) for name in ('text_scroll', 'image_redraw', 'image_replace')
                      for state in states)
        now = 0
        samples = 0

        def monotonic():
            nonlocal now
            now += 1
            return now

        def adb(args, **kwargs):
            nonlocal now, samples
            command = args[-1]
            if 'gfxinfo com.termux reset' in command:
                samples = 0
            if 'state.json' in command:
                phase, state = next(phases)
                if delayed_start:
                    # Ready within the documented window, then a 120-second phase.
                    now += 100 if state == 'ready' else 60
                return json.dumps({'phase': phase, 'state': state})
            if 'framestats' in command:
                if empty_frames:
                    return ''
                samples += 1
                timestamp = samples * 100
                return ('---PROFILEDATA---\nFlags,IntendedVsync,FrameCompleted\n'
                        f'0,{timestamp},{timestamp + 50}\n---PROFILEDATA---')
            if 'result.json' in command:
                return metadata
            return ''

        with patch.object(capture.subprocess, 'check_output', side_effect=adb), \
             patch.object(capture.time, 'sleep'), \
             patch.object(capture.time, 'monotonic', side_effect=monotonic), \
             patch('sys.argv', ['capture.py', '--serial', 'fake', '--remote-dir', '/run',
                                '--output', str(output)]):
            if empty_frames:
                with self.assertRaisesRegex(RuntimeError, 'no frames for text_scroll'):
                    capture.main()
                self.assertFalse((output / 'summary.json').exists())
                self.assertTrue((output / 'workload.json').exists())
            elif metadata == 'incomplete':
                with self.assertRaisesRegex(TimeoutError, 'result.json'):
                    capture.main()
                self.assertFalse((output / 'summary.json').exists())
            else:
                capture.main()
                for phase in ('text_scroll', 'image_redraw', 'image_replace'):
                    frames = json.loads((output / (phase + '-frames.json')).read_text())
                    expected = [200, 300, 400] if delayed_start else [200, 300]
                    self.assertEqual(expected, [frame['IntendedVsync'] for frame in frames])
                self.assertEqual({'complete': True}, json.loads((output / 'workload.json').read_text()))

    def test_collects_tail_after_done(self):
        self.run_capture('{"complete": true}')

    def test_incomplete_metadata_fails_run(self):
        self.run_capture('incomplete')

    def test_hidden_display_fails_run(self):
        self.run_capture('{"complete": true}', empty_frames=True)

    def test_late_ready_allows_full_length_phase(self):
        self.run_capture('{"complete": true}', delayed_start=True)


if __name__ == '__main__':
    unittest.main()
