<?php

use App\Http\Controllers\Api\AuthController;
use Illuminate\Support\Facades\Route;

/*
|--------------------------------------------------------------------------
| API Routes
|--------------------------------------------------------------------------
|
| Prefix: /api (otomatis dari RouteServiceProvider)
| Semua response dalam format JSON
|
*/

// =============================================================================
// AUTH — Public routes (tanpa token)
// =============================================================================
Route::prefix('auth')->group(function () {
    Route::post('/register', [AuthController::class, 'register'])
         ->name('auth.register');

    Route::post('/login', [AuthController::class, 'login'])
         ->name('auth.login');
});

// =============================================================================
// AUTH — Protected routes (butuh Sanctum token)
// =============================================================================
Route::prefix('auth')->middleware('auth:sanctum')->group(function () {
    Route::post('/logout', [AuthController::class, 'logout'])
         ->name('auth.logout');

    Route::post('/refresh', [AuthController::class, 'refreshToken'])
         ->name('auth.refresh');

    Route::get('/me', [AuthController::class, 'me'])
         ->name('auth.me');
});

// =============================================================================
// PROTECTED ROUTES — Role-based (contoh penggunaan middleware role)
// =============================================================================

// Mahasiswa-only routes
Route::middleware(['auth:sanctum', 'role:mahasiswa'])->prefix('student')->group(function () {
    // TODO: Activity tracking endpoints
});

// Dosen-only routes
Route::middleware(['auth:sanctum', 'role:dosen'])->prefix('lecturer')->group(function () {
    // TODO: Class management, reporting endpoints
});

// Admin routes (admin_prodi + super_admin)
Route::middleware(['auth:sanctum', 'role:admin_prodi,super_admin'])->prefix('admin')->group(function () {
    // TODO: User management, system configuration
});
