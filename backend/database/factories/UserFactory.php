<?php

namespace Database\Factories;

use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;
use Illuminate\Support\Str;

class UserFactory extends Factory
{
    protected $model = User::class;

    public function definition(): array
    {
        return [
            'id'                => Str::uuid()->toString(),
            'name'              => fake()->name(),
            'email'             => fake()->unique()->safeEmail(),
            'email_verified_at' => now(),
            'password'          => 'Password123',  // Auto-hashed via model cast
            'nim_nip'           => fake()->unique()->numerify('########'),
            'role'              => 'mahasiswa',
            'is_active'         => true,
            'gender'            => fake()->randomElement(['male', 'female']),
            'birth_date'        => fake()->date('Y-m-d', '2005-01-01'),
            'height_cm'         => fake()->randomFloat(1, 150, 190),
            'weight_kg'         => fake()->randomFloat(1, 45, 100),
            'remember_token'    => Str::random(10),
        ];
    }

    /**
     * Factory state untuk role dosen.
     */
    public function dosen(): static
    {
        return $this->state(fn () => [
            'role'    => 'dosen',
            'nim_nip' => fake()->unique()->numerify('##################'), // 18 digit NIP
        ]);
    }

    /**
     * Factory state untuk admin_prodi.
     */
    public function adminProdi(): static
    {
        return $this->state(fn () => [
            'role'    => 'admin_prodi',
            'nim_nip' => null,
        ]);
    }

    /**
     * Factory state untuk super_admin.
     */
    public function superAdmin(): static
    {
        return $this->state(fn () => [
            'role'    => 'super_admin',
            'nim_nip' => null,
        ]);
    }

    /**
     * Factory state untuk user inactive.
     */
    public function inactive(): static
    {
        return $this->state(fn () => [
            'is_active' => false,
        ]);
    }
}
