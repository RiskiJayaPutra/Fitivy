<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('step_logs', function (Blueprint $table) {
            // === PRIMARY KEY ===
            $table->uuid('id')->primary();

            // === RELASI ===
            $table->foreignUuid('session_id')                       // FK ke activity_sessions — log ini milik sesi mana
                  ->constrained('activity_sessions')
                  ->onDelete('cascade');                             // Sesi dihapus → semua log ikut hilang

            // === STEP DATA ===
            $table->timestamp('recorded_at');                       // Waktu pencatatan di device (bukan server time)
            $table->unsignedSmallInteger('step_count');              // Jumlah langkah dalam interval ini (biasanya 10 detik)
            $table->unsignedSmallInteger('cadence')->nullable();    // Langkah per menit — deteksi walking vs running
            $table->decimal('confidence', 3, 2)->nullable();        // Confidence level sensor (0.00 - 1.00)

            // === SENSOR RAW DATA ===
            $table->decimal('accelerometer_magnitude', 8, 4)       // Magnitude akselerometer — untuk validasi step count
                  ->nullable();                                     //   √(x² + y² + z²), outlier = pocket movement bukan step

            $table->timestamps();

            // === INDEXES ===
            $table->index('session_id');                            // Query: "semua log milik session X"
            $table->index('recorded_at');                           // Range query: "log antara jam 08:00-09:00"
            $table->index(['session_id', 'recorded_at']);           // Composite: time-series query per sesi
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('step_logs');
    }
};
