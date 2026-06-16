<?php

namespace Tests\Feature;

use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;
use App\Models\User;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Str;

class SessionApiTest extends TestCase
{
    use RefreshDatabase;

    private $user;
    private $token;

    protected function setUp(): void
    {
        parent::setUp();

        $this->user = User::create([
            'id' => Str::uuid(),
            'name' => 'Test User',
            'email' => 'test@fitivy.com',
            'identifier_number' => 'NIM001',
            'password' => Hash::make('password'),
            'role' => 'mahasiswa'
        ]);

        $this->token = $this->user->createToken('test-token')->plainTextToken;
    }

    public function test_api_session_sync_returns_201_under_500ms()
    {
        $startTime = microtime(true);

        $payload = [
            'id' => Str::uuid()->toString(),
            'started_at' => now()->subMinutes(30)->toIso8601String(),
            'ended_at' => now()->toIso8601String(),
            'activity_type' => 'running',
            'total_steps' => 3000,
            'duration_seconds' => 1800,
            'distance_meters' => 2.5,
            'calories_burned' => 150.0
        ];

        $response = $this->withHeaders([
            'Authorization' => 'Bearer ' . $this->token,
            'Accept' => 'application/json'
        ])->postJson('/api/sessions', $payload);

        $endTime = microtime(true);
        $executionTimeMs = ($endTime - $startTime) * 1000;

        $response->assertStatus(201);
        
        // Assert response time benchmark < 500ms
        $this->assertLessThan(500, $executionTimeMs, "API Response Time exceeded 500ms! (Took {$executionTimeMs}ms)");
    }
    
    public function test_api_rejects_unauthorized_access()
    {
        $response = $this->postJson('/api/sessions', []);
        $response->assertStatus(401);
    }
}
