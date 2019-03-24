import sys
from com.uwaterloo.watcag import CustomAnalysis as analysis_api

def run_timing_model_heuristic(base_dcp_filename):
    analysis_api.runTimingModelHeuristic(base_dcp_filename)

