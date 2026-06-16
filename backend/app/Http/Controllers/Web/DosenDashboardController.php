<?php

namespace App\Http\Controllers\Web;

use App\Http\Controllers\Controller;
use Illuminate\Http\Request;

class DosenDashboardController extends Controller
{
    public function index()
    {
        $totalActiveStudents = 45;
        $avgClassSteps = 6800;
        $topPerformer = [
            'name' => 'Budi Santoso',
            'steps' => 14500
        ];

        $students = [
            ['id' => 1, 'name' => 'Ahmad F.', 'avg_steps' => 9500, 'target_percent' => 95],
            ['id' => 2, 'name' => 'Budi S.', 'avg_steps' => 14500, 'target_percent' => 145],
            ['id' => 3, 'name' => 'Citra L.', 'avg_steps' => 8200, 'target_percent' => 82],
            ['id' => 4, 'name' => 'Dewi P.', 'avg_steps' => 5100, 'target_percent' => 51],
            ['id' => 5, 'name' => 'Eko M.', 'avg_steps' => 3200, 'target_percent' => 32],
            ['id' => 6, 'name' => 'Faisal B.', 'avg_steps' => 7800, 'target_percent' => 78],
            ['id' => 7, 'name' => 'Gita K.', 'avg_steps' => 10200, 'target_percent' => 102],
            ['id' => 8, 'name' => 'Hadi R.', 'avg_steps' => 4500, 'target_percent' => 45],
            ['id' => 9, 'name' => 'Indra T.', 'avg_steps' => 6200, 'target_percent' => 62],
            ['id' => 10, 'name' => 'Joko W.', 'avg_steps' => 2100, 'target_percent' => 21],
        ];

        return view('dosen.dashboard.index', compact(
            'totalActiveStudents',
            'avgClassSteps',
            'topPerformer',
            'students'
        ));
    }

    public function notifyMahasiswa(Request $request)
    {
        return back()->with('success', 'Push notification peringatan berhasil dikirim ke mahasiswa zona merah.');
    }
}
