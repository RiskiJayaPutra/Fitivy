<?php

namespace App\Http\Controllers\Web;

use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

class TargetController extends Controller
{
    public function index()
    {
        return view('dosen.targets.index');
    }

    public function store(Request $request)
    {
        // Store target logic here...
        return back()->with('success', 'Target aktivitas kelas berhasil diperbarui!');
    }

    public function previewTarget(Request $request)
    {
        $targetSteps = $request->input('target_steps', 10000);
        
        // Mock data logic based on 50 students
        $passed = 0;
        $failed = 0;
        
        // Simple logic: if target is < 5000, 45 pass. If 10000, 25 pass. If > 15000, 5 pass.
        if ($targetSteps <= 5000) {
            $passed = 45;
        } elseif ($targetSteps <= 10000) {
            $passed = 25;
        } else {
            $passed = 5;
        }
        $failed = 50 - $passed;

        return response()->json([
            'passed' => $passed,
            'failed' => $failed,
            'pass_percent' => round(($passed / 50) * 100)
        ]);
    }
}
