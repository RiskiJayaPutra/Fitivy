<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Resources\UserResource;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Log;
use Illuminate\Validation\Rules\Password;
use Illuminate\Validation\ValidationException;

class AuthController extends Controller
{
    // =========================================================================
    // REGISTER
    // =========================================================================

    /**
     * Register user baru.
     *
     * NIM/NIP digunakan untuk auto-detect role:
     *   - 8-12 digit  → mahasiswa
     *   - 18 digit    → dosen
     *   - admin_prodi & super_admin harus di-assign manual
     *
     * POST /api/auth/register
     */
    public function register(Request $request): JsonResponse
    {
        try {
            $validated = $request->validate([
                'name'         => ['required', 'string', 'max:255'],
                'email'        => ['required', 'string', 'email', 'max:255', 'unique:users,email'],
                'password'     => ['required', 'string', Password::min(8)->mixedCase()->numbers()],
                'nim_nip'      => ['required', 'string', 'max:20', 'unique:users,nim_nip'],
                'gender'       => ['nullable', 'in:male,female'],
                'birth_date'   => ['nullable', 'date', 'before:today'],
                'height_cm'    => ['nullable', 'numeric', 'min:50', 'max:300'],
                'weight_kg'    => ['nullable', 'numeric', 'min:20', 'max:500'],
                'device_id'    => ['nullable', 'string', 'max:255'],
                'device_model' => ['nullable', 'string', 'max:255'],
                'fcm_token'    => ['nullable', 'string', 'max:255'],
            ]);

            // Auto-detect role berdasarkan format NIM/NIP
            $role = User::detectRoleFromIdentifier($validated['nim_nip']);

            $user = DB::transaction(function () use ($validated, $role) {
                return User::create([
                    'name'         => $validated['name'],
                    'email'        => $validated['email'],
                    'password'     => $validated['password'],  // Auto-hashed via cast
                    'nim_nip'      => $validated['nim_nip'],
                    'role'         => $role,
                    'gender'       => $validated['gender'] ?? null,
                    'birth_date'   => $validated['birth_date'] ?? null,
                    'height_cm'    => $validated['height_cm'] ?? null,
                    'weight_kg'    => $validated['weight_kg'] ?? null,
                    'device_id'    => $validated['device_id'] ?? null,
                    'device_model' => $validated['device_model'] ?? null,
                    'fcm_token'    => $validated['fcm_token'] ?? null,
                ]);
            });

            // Buat Sanctum token — nama token = "auth_token" untuk identifikasi
            // Abilities disesuaikan dengan role untuk granular permission
            $abilities = $this->getAbilitiesForRole($role);
            $token = $user->createToken('auth_token', $abilities);

            Log::info('User registered', ['user_id' => $user->id, 'role' => $role]);

            return response()->json([
                'status'  => 'success',
                'message' => 'Registrasi berhasil',
                'data'    => [
                    'user'  => new UserResource($user),
                    'token' => [
                        'access_token' => $token->plainTextToken,
                        'token_type'   => 'Bearer',
                        'abilities'    => $abilities,
                    ],
                ],
            ], 201);

        } catch (ValidationException $e) {
            return response()->json([
                'status'  => 'error',
                'message' => 'Validasi gagal',
                'errors'  => $e->errors(),
            ], 422);

        } catch (\Exception $e) {
            Log::error('Registration failed', ['error' => $e->getMessage()]);

            return response()->json([
                'status'  => 'error',
                'message' => 'Registrasi gagal. Silakan coba lagi.',
            ], 500);
        }
    }

    // =========================================================================
    // LOGIN
    // =========================================================================

    /**
     * Login dengan email + password.
     * Return token + user data + role.
     * Mendukung login via email ATAU nim_nip.
     *
     * POST /api/auth/login
     */
    public function login(Request $request): JsonResponse
    {
        try {
            $validated = $request->validate([
                'login'        => ['required', 'string'],           // Bisa email atau NIM/NIP
                'password'     => ['required', 'string'],
                'device_id'    => ['nullable', 'string', 'max:255'],
                'device_model' => ['nullable', 'string', 'max:255'],
                'fcm_token'    => ['nullable', 'string', 'max:255'],
            ]);

            // Cari user by email ATAU nim_nip
            $user = User::where('email', $validated['login'])
                        ->orWhere('nim_nip', $validated['login'])
                        ->first();

            // Validasi kredensial
            if (!$user || !Hash::check($validated['password'], $user->password)) {
                return response()->json([
                    'status'  => 'error',
                    'message' => 'Email/NIM/NIP atau password salah',
                ], 401);
            }

            // Cek apakah akun aktif
            if (!$user->is_active) {
                return response()->json([
                    'status'  => 'error',
                    'message' => 'Akun Anda telah dinonaktifkan. Hubungi admin.',
                ], 403);
            }

            // Update device info jika dikirim dari client
            $user->update(array_filter([
                'device_id'    => $validated['device_id'] ?? null,
                'device_model' => $validated['device_model'] ?? null,
                'fcm_token'    => $validated['fcm_token'] ?? null,
            ]));

            // Revoke semua token lama — enforce single-device login
            $user->tokens()->delete();

            // Buat token baru dengan abilities sesuai role
            $abilities = $this->getAbilitiesForRole($user->role);
            $token = $user->createToken('auth_token', $abilities);

            Log::info('User logged in', ['user_id' => $user->id]);

            return response()->json([
                'status'  => 'success',
                'message' => 'Login berhasil',
                'data'    => [
                    'user'  => new UserResource($user),
                    'token' => [
                        'access_token' => $token->plainTextToken,
                        'token_type'   => 'Bearer',
                        'abilities'    => $abilities,
                    ],
                ],
            ]);

        } catch (ValidationException $e) {
            return response()->json([
                'status'  => 'error',
                'message' => 'Validasi gagal',
                'errors'  => $e->errors(),
            ], 422);

        } catch (\Exception $e) {
            Log::error('Login failed', ['error' => $e->getMessage()]);

            return response()->json([
                'status'  => 'error',
                'message' => 'Login gagal. Silakan coba lagi.',
            ], 500);
        }
    }

    // =========================================================================
    // LOGOUT
    // =========================================================================

    /**
     * Logout — revoke current token.
     *
     * POST /api/auth/logout
     */
    public function logout(Request $request): JsonResponse
    {
        try {
            // Revoke token yang sedang dipakai saja
            $request->user()->currentAccessToken()->delete();

            Log::info('User logged out', ['user_id' => $request->user()->id]);

            return response()->json([
                'status'  => 'success',
                'message' => 'Logout berhasil',
            ]);

        } catch (\Exception $e) {
            Log::error('Logout failed', ['error' => $e->getMessage()]);

            return response()->json([
                'status'  => 'error',
                'message' => 'Logout gagal',
            ], 500);
        }
    }

    // =========================================================================
    // REFRESH TOKEN
    // =========================================================================

    /**
     * Refresh token — hapus token lama, buat baru.
     * Ini bukan OAuth refresh token, tapi rotasi token sederhana
     * yang cocok untuk mobile app + Sanctum.
     *
     * POST /api/auth/refresh
     */
    public function refreshToken(Request $request): JsonResponse
    {
        try {
            $user = $request->user();

            // Hapus token yang sedang dipakai
            $user->currentAccessToken()->delete();

            // Buat token baru dengan abilities yang sama
            $abilities = $this->getAbilitiesForRole($user->role);
            $token = $user->createToken('auth_token', $abilities);

            Log::info('Token refreshed', ['user_id' => $user->id]);

            return response()->json([
                'status'  => 'success',
                'message' => 'Token berhasil diperbarui',
                'data'    => [
                    'user'  => new UserResource($user),
                    'token' => [
                        'access_token' => $token->plainTextToken,
                        'token_type'   => 'Bearer',
                        'abilities'    => $abilities,
                    ],
                ],
            ]);

        } catch (\Exception $e) {
            Log::error('Token refresh failed', ['error' => $e->getMessage()]);

            return response()->json([
                'status'  => 'error',
                'message' => 'Gagal memperbarui token',
            ], 500);
        }
    }

    // =========================================================================
    // ME — get current user
    // =========================================================================

    /**
     * Get current authenticated user data.
     *
     * GET /api/auth/me
     */
    public function me(Request $request): JsonResponse
    {
        return response()->json([
            'status' => 'success',
            'data'   => [
                'user' => new UserResource($request->user()),
            ],
        ]);
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Map role → token abilities.
     * Abilities digunakan oleh Sanctum untuk granular API permission.
     */
    private function getAbilitiesForRole(string $role): array
    {
        return match ($role) {
            User::ROLE_SUPER_ADMIN => ['*'],  // Full access
            User::ROLE_ADMIN_PRODI => [
                'user:read', 'user:manage',
                'class:read', 'class:manage',
                'activity:read',
                'report:read', 'report:export',
                'badge:read', 'badge:manage',
                'target:read', 'target:manage',
                'qr:read',
            ],
            User::ROLE_DOSEN => [
                'user:read',
                'class:read', 'class:manage',
                'activity:read',
                'report:read', 'report:export',
                'target:read', 'target:manage',
                'qr:read', 'qr:generate',
            ],
            User::ROLE_MAHASISWA => [
                'activity:read', 'activity:write',
                'badge:read',
                'target:read',
                'qr:checkin',
                'profile:read', 'profile:write',
            ],
            default => [],
        };
    }
}
