<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('activity_targets', function (Blueprint $table) {
            // === PRIMARY KEY ===
            $table->uuid('id')->primary();

            // === RELASI ===
            $table->foreignUuid('user_id')                          // FK ke users — target ini untuk siapa
                  ->constrained('users')
                  ->onDelete('cascade');
            $table->foreignUuid('assigned_by')                      // FK ke users — siapa yang assign target ini
                  ->nullable()                                      //   null = self-assigned oleh mahasiswa sendiri
                  ->constrained('users')
                  ->onDelete('set null');                            // Jika assigner dihapus, target tetap ada

            // === TARGET DEFINITION ===
            $table->enum('target_type', [                           // Jenis target:
                'daily_steps',                                      //   daily_steps  = langkah per hari
                'daily_distance',                                   //   daily_distance = jarak per hari (meter)
                'daily_calories',                                   //   daily_calories = kalori per hari
                'weekly_sessions',                                  //   weekly_sessions = jumlah sesi per minggu
                'daily_duration'                                    //   daily_duration = durasi per hari (detik)
            ]);
            $table->unsignedInteger('target_value');                // Nilai target, misal 10000 (steps) atau 5000 (meters)

            // === PERIOD ===
            $table->date('start_date');                             // Tanggal mulai berlaku target
            $table->date('end_date')->nullable();                   // Tanggal berakhir — null = berlaku selamanya
            $table->boolean('is_active')->default(true);            // Bisa di-deactivate tanpa delete

            $table->timestamps();

            // === INDEXES ===
            $table->index('user_id');                               // Query: "semua target milik user X"
            $table->index(['user_id', 'target_type']);              // Composite: "target daily_steps milik user X"
            $table->index(['user_id', 'is_active']);                // Composite: "target aktif milik user X"
            $table->index('start_date');                            // Filter by periode
            $table->index('assigned_by');                           // Query: "semua target yang di-assign dosen Y"
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('activity_targets');
    }
};
