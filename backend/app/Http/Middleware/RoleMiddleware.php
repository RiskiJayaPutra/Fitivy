<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

/**
 * RoleMiddleware — middleware untuk role-based access control.
 *
 * Usage di route:
 *   ->middleware('role:dosen,admin_prodi')        // Hanya dosen DAN admin_prodi
 *   ->middleware('role:super_admin')               // Hanya super_admin
 *   ->middleware('role:mahasiswa,dosen,admin_prodi,super_admin')  // Semua role
 *
 * Register di Kernel.php:
 *   'role' => \App\Http\Middleware\RoleMiddleware::class,
 *
 * Atau di bootstrap/app.php (Laravel 11+):
 *   ->withMiddleware(function (Middleware $middleware) {
 *       $middleware->alias(['role' => RoleMiddleware::class]);
 *   })
 */
class RoleMiddleware
{
    public function handle(Request $request, Closure $next, string ...$roles): Response
    {
        // Guard: pastikan user sudah authenticated
        if (!$request->user()) {
            return response()->json([
                'status'  => 'error',
                'message' => 'Unauthenticated. Silakan login terlebih dahulu.',
            ], 401);
        }

        // Guard: pastikan akun aktif
        if (!$request->user()->is_active) {
            return response()->json([
                'status'  => 'error',
                'message' => 'Akun Anda telah dinonaktifkan. Hubungi admin.',
            ], 403);
        }

        // Cek apakah role user ada di daftar role yang diizinkan
        if (!in_array($request->user()->role, $roles, true)) {
            return response()->json([
                'status'  => 'error',
                'message' => 'Anda tidak memiliki akses ke resource ini.',
                'required_roles' => $roles,
                'your_role'      => $request->user()->role,
            ], 403);
        }

        return $next($request);
    }
}
