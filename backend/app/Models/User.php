<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Concerns\HasUuids;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;
use Laravel\Sanctum\HasApiTokens;

class User extends Authenticatable
{
    use HasApiTokens, HasFactory, HasUuids, Notifiable;

    // =========================================================================
    // MASS ASSIGNMENT
    // =========================================================================

    protected $fillable = [
        'name',
        'email',
        'password',
        'nim_nip',
        'role',
        'is_active',
        'avatar_url',
        'height_cm',
        'weight_kg',
        'birth_date',
        'gender',
        'fcm_token',
        'device_id',
        'device_model',
    ];

    protected $hidden = [
        'password',
        'remember_token',
        'fcm_token',       // FCM token tidak perlu di-expose ke client
    ];

    protected function casts(): array
    {
        return [
            'email_verified_at' => 'datetime',
            'password'          => 'hashed',     // Auto-hash via Laravel 11 attribute casting
            'birth_date'        => 'date',
            'is_active'         => 'boolean',
            'height_cm'         => 'float',
            'weight_kg'         => 'float',
        ];
    }

    // =========================================================================
    // ROLE CONSTANTS — single source of truth, dipakai di migration enum juga
    // =========================================================================

    public const ROLE_MAHASISWA   = 'mahasiswa';
    public const ROLE_DOSEN       = 'dosen';
    public const ROLE_ADMIN_PRODI = 'admin_prodi';
    public const ROLE_SUPER_ADMIN = 'super_admin';

    public const ROLES = [
        self::ROLE_MAHASISWA,
        self::ROLE_DOSEN,
        self::ROLE_ADMIN_PRODI,
        self::ROLE_SUPER_ADMIN,
    ];

    // =========================================================================
    // ROLE HELPERS
    // =========================================================================

    public function isMahasiswa(): bool
    {
        return $this->role === self::ROLE_MAHASISWA;
    }

    public function isDosen(): bool
    {
        return $this->role === self::ROLE_DOSEN;
    }

    public function isAdminProdi(): bool
    {
        return $this->role === self::ROLE_ADMIN_PRODI;
    }

    public function isSuperAdmin(): bool
    {
        return $this->role === self::ROLE_SUPER_ADMIN;
    }

    /**
     * Cek apakah user memiliki salah satu role yang diberikan.
     * Usage: $user->hasRole('dosen', 'super_admin')
     */
    public function hasRole(string ...$roles): bool
    {
        return in_array($this->role, $roles, true);
    }

    // =========================================================================
    // NIM/NIP DETECTION — untuk auto-assign role saat register
    // =========================================================================

    /**
     * Deteksi role berdasarkan format NIM/NIP:
     *   - NIM mahasiswa: 8-12 digit angka (contoh: "20230001")
     *   - NIP dosen:     18 digit angka (contoh: "198507152010011001")
     *
     * Admin roles harus di-assign manual oleh super_admin.
     */
    public static function detectRoleFromIdentifier(string $nimNip): string
    {
        $cleaned = preg_replace('/\s+/', '', $nimNip);

        // NIP dosen: 18 digit (format NIP PNS Indonesia)
        if (preg_match('/^\d{18}$/', $cleaned)) {
            return self::ROLE_DOSEN;
        }

        // NIM mahasiswa: 8-12 digit
        if (preg_match('/^\d{8,12}$/', $cleaned)) {
            return self::ROLE_MAHASISWA;
        }

        // Fallback: mahasiswa (paling aman, least privilege)
        return self::ROLE_MAHASISWA;
    }

    // =========================================================================
    // RELATIONSHIPS
    // =========================================================================

    public function activitySessions()
    {
        return $this->hasMany(\App\Models\ActivitySession::class);
    }

    public function badges()
    {
        return $this->belongsToMany(\App\Models\Badge::class, 'user_badges')
                    ->withPivot('earned_at', 'progress_value')
                    ->withTimestamps();
    }

    public function activityTargets()
    {
        return $this->hasMany(\App\Models\ActivityTarget::class);
    }

    public function classes()
    {
        return $this->belongsToMany(\App\Models\ClassModel::class, 'class_user', 'user_id', 'class_id')
                    ->withPivot('enrolled_at')
                    ->withTimestamps();
    }

    public function taughtClasses()
    {
        return $this->hasMany(\App\Models\ClassModel::class, 'lecturer_id');
    }
}
