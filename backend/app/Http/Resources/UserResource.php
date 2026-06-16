<?php

namespace App\Http\Resources;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * UserResource — standardisasi JSON response untuk User model.
 *
 * Memastikan format response konsisten di seluruh API:
 *   - Tidak expose password, remember_token, fcm_token
 *   - Tanggal dalam format ISO 8601
 *   - Null fields tetap di-include agar Android client tidak perlu null-check key existence
 */
class UserResource extends JsonResource
{
    public function toArray(Request $request): array
    {
        return [
            // Identitas
            'id'         => $this->id,
            'name'       => $this->name,
            'email'      => $this->email,
            'nim_nip'    => $this->nim_nip,

            // Role & Status
            'role'       => $this->role,
            'is_active'  => $this->is_active,

            // Profile
            'avatar_url' => $this->avatar_url,
            'height_cm'  => $this->height_cm,
            'weight_kg'  => $this->weight_kg,
            'birth_date' => $this->birth_date?->format('Y-m-d'),
            'gender'     => $this->gender,

            // Device (hanya tampilkan device_id, bukan model — kurang relevan untuk client)
            'device_id'  => $this->device_id,

            // Timestamps
            'email_verified_at' => $this->email_verified_at?->toIso8601String(),
            'created_at'        => $this->created_at?->toIso8601String(),
            'updated_at'        => $this->updated_at?->toIso8601String(),
        ];
    }
}
