<?php

use Illuminate\Support\Facades\Route;
use App\Http\Controllers\Web\StudentController;
use App\Http\Controllers\Web\TargetController;
use App\Http\Controllers\Web\ReportController;

// Existing routes (assumed)
Route::get('/', function () {
    return view('welcome');
});

// Admin/Dosen Routes
Route::prefix('dosen')->name('dosen.')->group(function () {
    
    // Dashboard
    Route::get('/dashboard', [DosenDashboardController::class, 'index'])->name('dashboard.index');
    Route::post('/dashboard/notify', [DosenDashboardController::class, 'notifyMahasiswa'])->name('dashboard.notify');
    
    // Student Detail
    Route::get('/student/{id}', [StudentController::class, 'show'])->name('student.show');
    Route::post('/student/{id}/note', [StudentController::class, 'addNote'])->name('student.note');

    // Target Management
    Route::get('/targets', [TargetController::class, 'index'])->name('targets.index');
    Route::post('/targets', [TargetController::class, 'store'])->name('targets.store');
    Route::get('/targets/preview', [TargetController::class, 'previewTarget'])->name('targets.preview');

    // Reports
    Route::get('/reports', [ReportController::class, 'index'])->name('reports.index');
    Route::get('/reports/export/csv', [ReportController::class, 'exportCsv'])->name('reports.export.csv');
    Route::get('/reports/export/pdf', [ReportController::class, 'exportPdf'])->name('reports.export.pdf');
});
