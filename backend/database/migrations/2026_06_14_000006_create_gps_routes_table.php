<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('gps_routes', function (Blueprint $table) {
            // === PRIMARY KEY ===
            $table->uuid('id')->primary();

            // === RELASI ===
            $table->foreignUuid('session_id')                       // FK ke activity_sessions — rute ini milik sesi mana
                  ->constrained('activity_sessions')
                  ->onDelete('cascade');

            // === GPS COORDINATE ===
            $table->decimal('latitude', 10, 7);                     // Latitude (7 decimal = akurasi ~1cm)
            $table->decimal('longitude', 10, 7);                    // Longitude (7 decimal = akurasi ~1cm)
            $table->decimal('altitude_meters', 7, 2)->nullable();   // Altitude dalam meter — untuk hitung elevation gain
            $table->decimal('accuracy_meters', 6, 2)->nullable();   // Akurasi GPS dalam meter — filter noise (>50m = buang)
            $table->decimal('speed_ms', 6, 2)->nullable();          // Speed dari GPS dalam m/s — cross-validate dgn step cadence
            $table->decimal('bearing', 5, 2)->nullable();           // Arah hadap (0-360°) — untuk smoothing polyline

            // === ORDERING ===
            $table->unsignedInteger('sequence')->default(0);        // Urutan titik dalam polyline (0, 1, 2, ...)
            $table->timestamp('recorded_at');                       // Waktu pencatatan GPS point di device

            $table->timestamps();

            // === INDEXES ===
            $table->index('session_id');                            // Query: "semua titik GPS dari session X"
            $table->index(['session_id', 'sequence']);               // Composite: render polyline in order
            $table->index('recorded_at');                           // Range query by waktu
            $table->index(['latitude', 'longitude']);               // Geospatial query sederhana (bounding box)
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('gps_routes');
    }
};
