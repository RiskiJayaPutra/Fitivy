<?php

namespace App\Http\Controllers\Web;

use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

class StudentController extends Controller
{
    public function show($id)
    {
        // Mock data for student
        $student = [
            'id' => $id,
            'name' => 'Joko Widodo',
            'nim' => '210001',
            'status' => 'Merah', // < 50%
            'target_percent' => 21,
            'avg_steps' => 2100
        ];

        // Timeline mock data
        $timeline = [
            ['date' => '14 Juni 2026', 'type' => 'running', 'steps' => 3000, 'duration' => '30m', 'calories' => 150],
            ['date' => '12 Juni 2026', 'type' => 'walking', 'steps' => 1200, 'duration' => '15m', 'calories' => 50],
        ];

        // Route GPS Mock data (Jakarta area)
        $routes = [
            [-6.200000, 106.816666],
            [-6.201000, 106.817000],
            [-6.202000, 106.818000],
            [-6.202500, 106.819500],
        ];

        return view('dosen.student.show', compact('student', 'timeline', 'routes'));
    }

    public function addNote(Request $request, $id)
    {
        // Add note logic
        return back()->with('success', 'Catatan evaluasi berhasil ditambahkan.');
    }
}
