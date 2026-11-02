#!/usr/bin/env python3
"""Synthetic parser tests only. These are not device benchmark results."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("report", Path(__file__).with_name("benchmark_report.py"))
report = importlib.util.module_from_spec(spec)
spec.loader.exec_module(report)


def rows():
    base = dict(device="Synthetic parser fixture", android="14", sdk="34", source_revision="a" * 40,
                error="", ttff_ms="100", seek_ms="20", dropped_frames="0", cpu_percent_one_core="10",
                active_audio="0", active_video="0", wake_locks="0", elapsed_s="1800", pss_kb="100", fd_count="40",
                sha256="b" * 64, size_bytes="12345", duration_s="15", status="pass")
    refs = [dict(base, kind="reference", file="h264.mp4", codec="h264", width="1920", height="1080", bit_depth="8", transfer="bt709"),
            dict(base, kind="reference", file="hevc.mp4", codec="hevc", width="3840", height="2160", bit_depth="8", transfer="bt709"),
            dict(base, kind="reference", file="hdr.mp4", codec="hevc", width="1920", height="1080", bit_depth="10", transfer="smpte2084")]
    return refs + [dict(base, kind="background_sample"), dict(base, kind="background_release")]


class ReportTest(unittest.TestCase):
    def test_complete_gate(self):
        text, passed = report.summarize(rows())
        self.assertTrue(passed)
        self.assertIn("Synthetic parser fixture", text)

    def test_missing_reference(self):
        self.assertFalse(report.summarize(rows()[1:])[1])

    def test_unknown_wake_locks(self):
        data = rows(); data[-1]["wake_locks"] = "unknown"; data[-1]["status"] = "inconclusive"
        self.assertFalse(report.summarize(data)[1])

    def test_short_cannot_pass_even_when_label_is_pass(self):
        data = rows(); data[-1]["elapsed_s"] = "30"
        self.assertFalse(report.summarize(data)[1])

    def test_negative_timing_or_failure_cannot_pass(self):
        data = rows(); data[0]["ttff_ms"] = "-1"
        self.assertFalse(report.summarize(data)[1])
        data = rows(); data[-2]["status"] = "fail"
        self.assertFalse(report.summarize(data)[1])

    def test_no_hdr_or_4k_cannot_pass(self):
        data = rows(); data[2]["bit_depth"] = "8"
        self.assertFalse(report.summarize(data)[1])
        data = rows(); data[1]["width"] = "1920"
        self.assertFalse(report.summarize(data)[1])

    def test_missing_or_mixed_revision_rejected(self):
        data = rows(); data[0]["source_revision"] = "local-unknown"
        with self.assertRaises(ValueError): report.summarize(data)
        data = rows()
        for row in data: row["source_revision"] = "local-unknown"
        with self.assertRaises(ValueError): report.summarize(data)

    def test_partial_run_no_release(self):
        self.assertFalse(report.summarize(rows()[:-1])[1])

    def test_empty_rejected(self):
        with self.assertRaises(ValueError): report.summarize([])


if __name__ == "__main__":
    unittest.main()
