<?php

namespace Database\Seeders;

use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Str;
use Carbon\Carbon;

class LoadTestSeeder extends Seeder
{
    /**
     * Run the database seeds.
     * Menggunakan konsep chunking (upsert) untuk menghindari PHP Memory Exhaustion
     * saat meng-generate 15.000 records (500 mahasiswa x 30 hari).
     */
    public function run(): void
    {
        $this->command->info('Memulai LoadTestSeeder...');

        $numUsers = 500;
        $days = 30;
        
        $users = [];
        $sessions = [];

        // 1. BATCH CREATE USERS (Chunk 100)
        $this->command->info("Seeding $numUsers mahasiswa...");
        for ($i = 1; $i <= $numUsers; $i++) {
            $users[] = [
                'id' => Str::uuid(),
                'name' => "Mahasiswa Load Test $i",
                'email' => "mahasiswa$i@loadtest.fitivy.com",
                'identifier_number' => "NIM1000$i",
                'password' => Hash::make('password'),
                'role' => 'mahasiswa',
                'created_at' => now(),
                'updated_at' => now(),
            ];

            if (count($users) >= 100) {
                DB::table('users')->insert($users);
                $users = [];
            }
        }
        if (count($users) > 0) {
            DB::table('users')->insert($users);
        }

        // 2. BATCH CREATE ACTIVITY SESSIONS (Chunk 500)
        $this->command->info("Seeding $days hari aktivitas untuk masing-masing mahasiswa...");
        $userIds = DB::table('users')->where('email', 'like', '%@loadtest.fitivy.com')->pluck('id');

        foreach ($userIds as $userId) {
            for ($d = 0; $d < $days; $d++) {
                $startedAt = Carbon::now()->subDays($d)->setTime(rand(6, 17), 0, 0);
                
                $sessions[] = [
                    'id' => Str::uuid(),
                    'user_id' => $userId,
                    'started_at' => $startedAt,
                    'ended_at' => (clone $startedAt)->addMinutes(rand(15, 60)),
                    'activity_type' => ['walking', 'running', 'cycling'][rand(0, 2)],
                    'total_steps' => rand(1000, 15000),
                    'duration_seconds' => rand(900, 3600),
                    'distance_meters' => rand(500, 10000) / 10.0,
                    'calories_burned' => rand(50, 600) / 10.0,
                    'status' => 'completed',
                    'sync_status' => 'synced',
                    'created_at' => now(),
                    'updated_at' => now(),
                ];

                // Chunk insert
                if (count($sessions) >= 500) {
                    DB::table('activity_sessions')->insert($sessions);
                    $sessions = [];
                }
            }
        }

        if (count($sessions) > 0) {
            DB::table('activity_sessions')->insert($sessions);
        }

        $this->command->info('Load test data berhasil dibuat!');
    }
}
