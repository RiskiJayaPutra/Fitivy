<?php

namespace App\Http\Controllers\Web;

use App\Http\Controllers\Controller;
use Illuminate\Http\Request;
use Barryvdh\DomPDF\Facade\Pdf;
use Symfony\Component\HttpFoundation\StreamedResponse;

class ReportController extends Controller
{
    public function index()
    {
        return view('dosen.reports.index');
    }

    public function exportCsv(Request $request)
    {
        $startDate = $request->input('start_date', now()->subDays(30)->toDateString());
        $endDate = $request->input('end_date', now()->toDateString());

        // Mock data
        $data = [
            ['NIM' => '210001', 'Nama' => 'Joko Widodo', 'Total Langkah' => 45000, 'Rata-rata Harian' => 1500, 'Status' => 'Gagal'],
            ['NIM' => '210002', 'Nama' => 'Ahmad F.', 'Total Langkah' => 300000, 'Rata-rata Harian' => 10000, 'Status' => 'Lulus'],
        ];

        $response = new StreamedResponse(function() use ($data) {
            $handle = fopen('php://output', 'w');
            fputcsv($handle, ['NIM', 'Nama', 'Total Langkah', 'Rata-rata Harian', 'Status']);

            foreach ($data as $row) {
                fputcsv($handle, [$row['NIM'], $row['Nama'], $row['Total Langkah'], $row['Rata-rata Harian'], $row['Status']]);
            }

            fclose($handle);
        }, 200, [
            'Content-Type' => 'text/csv',
            'Content-Disposition' => 'attachment; filename="Laporan_Aktivitas_' . date('Y-m-d') . '.csv"',
        ]);

        return $response;
    }

    public function exportPdf(Request $request)
    {
        $startDate = $request->input('start_date', now()->subDays(30)->toDateString());
        $endDate = $request->input('end_date', now()->toDateString());

        // Mock data
        $students = [
            ['nim' => '210001', 'name' => 'Joko Widodo', 'total_steps' => 45000, 'status' => 'Gagal'],
            ['nim' => '210002', 'name' => 'Ahmad F.', 'total_steps' => 300000, 'status' => 'Lulus'],
            ['nim' => '210003', 'name' => 'Budi S.', 'total_steps' => 350000, 'status' => 'Lulus'],
        ];

        $pdf = Pdf::loadView('dosen.reports.pdf', compact('students', 'startDate', 'endDate'));
        
        return $pdf->download('Laporan_Aktivitas_' . date('Y-m-d') . '.pdf');
    }
}
