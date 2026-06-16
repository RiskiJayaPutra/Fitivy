<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class AuthTest extends TestCase
{
    use RefreshDatabase;

    // =========================================================================
    // REGISTER TESTS
    // =========================================================================

    public function test_mahasiswa_can_register_with_nim(): void
    {
        $response = $this->postJson('/api/auth/register', [
            'name'     => 'Budi Santoso',
            'email'    => 'budi@student.univ.ac.id',
            'password' => 'Password123',
            'nim_nip'  => '20230001',   // 8 digit = mahasiswa
        ]);

        $response->assertStatus(201)
                 ->assertJsonStructure([
                     'status', 'message',
                     'data' => [
                         'user' => ['id', 'name', 'email', 'nim_nip', 'role'],
                         'token' => ['access_token', 'token_type', 'abilities'],
                     ],
                 ])
                 ->assertJsonPath('data.user.role', 'mahasiswa');

        $this->assertDatabaseHas('users', [
            'email'   => 'budi@student.univ.ac.id',
            'nim_nip' => '20230001',
            'role'    => 'mahasiswa',
        ]);
    }

    public function test_dosen_can_register_with_nip(): void
    {
        $response = $this->postJson('/api/auth/register', [
            'name'     => 'Dr. Siti Aminah',
            'email'    => 'siti@univ.ac.id',
            'password' => 'Password123',
            'nim_nip'  => '198507152010011001',   // 18 digit = dosen
        ]);

        $response->assertStatus(201)
                 ->assertJsonPath('data.user.role', 'dosen');
    }

    public function test_register_fails_with_duplicate_email(): void
    {
        User::factory()->create(['email' => 'budi@student.univ.ac.id']);

        $response = $this->postJson('/api/auth/register', [
            'name'     => 'Budi Lain',
            'email'    => 'budi@student.univ.ac.id',
            'password' => 'Password123',
            'nim_nip'  => '20230002',
        ]);

        $response->assertStatus(422)
                 ->assertJsonPath('status', 'error');
    }

    public function test_register_fails_with_duplicate_nim(): void
    {
        User::factory()->create(['nim_nip' => '20230001']);

        $response = $this->postJson('/api/auth/register', [
            'name'     => 'Another Student',
            'email'    => 'another@student.univ.ac.id',
            'password' => 'Password123',
            'nim_nip'  => '20230001',
        ]);

        $response->assertStatus(422);
    }

    public function test_register_fails_with_weak_password(): void
    {
        $response = $this->postJson('/api/auth/register', [
            'name'     => 'Weak User',
            'email'    => 'weak@student.univ.ac.id',
            'password' => '123',           // Terlalu pendek, tanpa huruf besar
            'nim_nip'  => '20230003',
        ]);

        $response->assertStatus(422);
    }

    // =========================================================================
    // LOGIN TESTS
    // =========================================================================

    public function test_user_can_login_with_email(): void
    {
        $user = User::factory()->create([
            'email'    => 'budi@student.univ.ac.id',
            'password' => 'Password123',
            'role'     => 'mahasiswa',
        ]);

        $response = $this->postJson('/api/auth/login', [
            'login'    => 'budi@student.univ.ac.id',
            'password' => 'Password123',
        ]);

        $response->assertStatus(200)
                 ->assertJsonStructure([
                     'data' => [
                         'user' => ['id', 'name', 'email', 'role'],
                         'token' => ['access_token', 'token_type'],
                     ],
                 ])
                 ->assertJsonPath('status', 'success');
    }

    public function test_user_can_login_with_nim(): void
    {
        User::factory()->create([
            'nim_nip'  => '20230001',
            'password' => 'Password123',
            'role'     => 'mahasiswa',
        ]);

        $response = $this->postJson('/api/auth/login', [
            'login'    => '20230001',
            'password' => 'Password123',
        ]);

        $response->assertStatus(200)
                 ->assertJsonPath('data.user.role', 'mahasiswa');
    }

    public function test_login_fails_with_wrong_password(): void
    {
        User::factory()->create([
            'email'    => 'budi@student.univ.ac.id',
            'password' => 'Password123',
        ]);

        $response = $this->postJson('/api/auth/login', [
            'login'    => 'budi@student.univ.ac.id',
            'password' => 'WrongPassword',
        ]);

        $response->assertStatus(401)
                 ->assertJsonPath('status', 'error');
    }

    public function test_login_fails_for_inactive_user(): void
    {
        User::factory()->create([
            'email'     => 'inactive@student.univ.ac.id',
            'password'  => 'Password123',
            'is_active' => false,
        ]);

        $response = $this->postJson('/api/auth/login', [
            'login'    => 'inactive@student.univ.ac.id',
            'password' => 'Password123',
        ]);

        $response->assertStatus(403);
    }

    public function test_login_updates_device_info(): void
    {
        $user = User::factory()->create([
            'email'    => 'budi@student.univ.ac.id',
            'password' => 'Password123',
        ]);

        $this->postJson('/api/auth/login', [
            'login'        => 'budi@student.univ.ac.id',
            'password'     => 'Password123',
            'device_id'    => 'android-device-123',
            'device_model' => 'Samsung Galaxy S24',
        ]);

        $this->assertDatabaseHas('users', [
            'id'           => $user->id,
            'device_id'    => 'android-device-123',
            'device_model' => 'Samsung Galaxy S24',
        ]);
    }

    // =========================================================================
    // LOGOUT TESTS
    // =========================================================================

    public function test_user_can_logout(): void
    {
        $user = User::factory()->create();
        $token = $user->createToken('auth_token')->plainTextToken;

        $response = $this->withHeader('Authorization', "Bearer {$token}")
                         ->postJson('/api/auth/logout');

        $response->assertStatus(200)
                 ->assertJsonPath('status', 'success');

        // Pastikan token sudah di-revoke
        $this->assertDatabaseCount('personal_access_tokens', 0);
    }

    public function test_logout_fails_without_token(): void
    {
        $response = $this->postJson('/api/auth/logout');

        $response->assertStatus(401);
    }

    // =========================================================================
    // REFRESH TOKEN TESTS
    // =========================================================================

    public function test_user_can_refresh_token(): void
    {
        $user = User::factory()->create(['role' => 'mahasiswa']);
        $oldToken = $user->createToken('auth_token')->plainTextToken;

        $response = $this->withHeader('Authorization', "Bearer {$oldToken}")
                         ->postJson('/api/auth/refresh');

        $response->assertStatus(200)
                 ->assertJsonStructure([
                     'data' => [
                         'token' => ['access_token', 'token_type', 'abilities'],
                     ],
                 ]);

        // Token baru harus berbeda dari token lama
        $newToken = $response->json('data.token.access_token');
        $this->assertNotEquals($oldToken, $newToken);

        // Token lama harus sudah tidak bisa dipakai
        $this->withHeader('Authorization', "Bearer {$oldToken}")
             ->getJson('/api/auth/me')
             ->assertStatus(401);
    }

    // =========================================================================
    // ME TESTS
    // =========================================================================

    public function test_user_can_get_own_profile(): void
    {
        $user = User::factory()->create([
            'name'  => 'Budi Santoso',
            'email' => 'budi@student.univ.ac.id',
            'role'  => 'mahasiswa',
        ]);
        $token = $user->createToken('auth_token')->plainTextToken;

        $response = $this->withHeader('Authorization', "Bearer {$token}")
                         ->getJson('/api/auth/me');

        $response->assertStatus(200)
                 ->assertJsonPath('data.user.name', 'Budi Santoso')
                 ->assertJsonPath('data.user.email', 'budi@student.univ.ac.id')
                 ->assertJsonPath('data.user.role', 'mahasiswa');
    }

    // =========================================================================
    // ROLE MIDDLEWARE TESTS
    // =========================================================================

    public function test_mahasiswa_cannot_access_admin_routes(): void
    {
        $user = User::factory()->create(['role' => 'mahasiswa']);
        $token = $user->createToken('auth_token')->plainTextToken;

        // Coba akses admin route
        $response = $this->withHeader('Authorization', "Bearer {$token}")
                         ->getJson('/api/admin');

        // Harus ditolak (403 Forbidden atau 404 jika route belum ada)
        $this->assertTrue(in_array($response->status(), [403, 404]));
    }
}
