<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('activity_sessions', function (Blueprint $table) {
            // === PRIMARY KEY ===
            $table->uuid('id')->primary();

            // === RELASI ===
            $table->foreignUuid('user_id')                          // FK ke users — siapa yang melakukan aktivitas
                  ->constrained('users')
                  ->onDelete('cascade');

            // === SESSION METADATA ===
            $table->enum('activity_type', [                         // Jenis aktivitas — menentukan icon & formula kalori
                'walking', 'running', 'cycling'
            ])->default('walking');
            $table->enum('status', [                                // Status sesi:
                'active',                                           //   active = sedang tracking
                'paused',                                           //   paused = user pause sementara
                'completed',                                        //   completed = selesai normal
                'abandoned'                                         //   abandoned = force-stop / crash
            ])->default('active');

            // === TIMING ===
            $table->timestamp('started_at');                        // Waktu mulai tracking (dari device)
            $table->timestamp('ended_at')->nullable();              // Waktu selesai — null saat masih active
            $table->unsignedInteger('duration_seconds')             // Total durasi aktif (exclude pause time)
                  ->default(0);

            // === SUMMARY METRICS ===
            $table->unsignedInteger('total_steps')->default(0);     // Total langkah dalam sesi ini
            $table->decimal('distance_meters', 10, 2)               // Jarak tempuh dalam meter (dari GPS + step length)
                  ->default(0);
            $table->decimal('calories_burned', 8, 2)                // Kalori terbakar (Mifflin-St Jeor + MET)
                  ->default(0);
            $table->decimal('avg_speed_kmh', 5, 2)                  // Kecepatan rata-rata km/jam
                  ->nullable();
            $table->decimal('max_speed_kmh', 5, 2)                  // Kecepatan maksimum km/jam
                  ->nullable();

            // === DEVICE INFO ===
            $table->string('device_model')->nullable();             // Model HP saat sesi — untuk debug sensor drift
            $table->unsignedSmallInteger('battery_start')           // Level baterai saat mulai (%) — korelasi akurasi sensor
                  ->nullable();
            $table->unsignedSmallInteger('battery_end')             // Level baterai saat selesai (%) — deteksi battery drain
                  ->nullable();

            // === SYNC ===
            $table->boolean('is_synced')->default(false);           // Flag: sudah sync ke server atau belum
            $table->timestamp('synced_at')->nullable();             // Kapan terakhir sync

            $table->timestamps();

            // === INDEXES ===
            $table->index('user_id');                               // Query: "semua sesi milik user X"
            $table->index('status');                                // Filter sesi active/completed
            $table->index('activity_type');                         // Filter by jenis aktivitas
            $table->index('started_at');                            // Sort & range query by tanggal
            $table->index(['user_id', 'started_at']);               // Composite: "sesi user X pada range tanggal Y"
            $table->index(['user_id', 'status']);                   // Composite: "sesi active milik user X"
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('activity_sessions');
    }
};
