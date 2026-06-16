<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('badges', function (Blueprint $table) {
            // === PRIMARY KEY ===
            $table->uuid('id')->primary();

            // === BADGE INFO ===
            $table->string('name')->unique();                       // Nama badge, misal "Marathon Runner" — unique agar tidak duplikat
            $table->string('slug')->unique();                       // URL-friendly slug, misal "marathon-runner" — untuk API lookup
            $table->text('description');                             // Deskripsi cara mendapatkan badge ini
            $table->string('icon_url')->nullable();                 // URL icon badge (Firebase Storage)

            // === CRITERIA ===
            $table->string('criteria_type');                        // Tipe kriteria: "total_steps", "total_sessions", "streak_days", "total_distance"
            $table->unsignedInteger('criteria_value');              // Nilai threshold, misal 10000 untuk "10K Steps"

            // === DISPLAY ===
            $table->enum('tier', ['bronze', 'silver', 'gold',      // Tier badge — menentukan warna/prioritas tampilan
                'platinum'])->default('bronze');
            $table->unsignedSmallInteger('sort_order')              // Urutan tampil di UI (ascending)
                  ->default(0);
            $table->boolean('is_active')->default(true);            // Bisa di-deactivate tanpa delete

            $table->timestamps();

            // === INDEXES ===
            $table->index('criteria_type');                         // Query: "semua badge bertipe total_steps"
            $table->index('tier');                                  // Filter by tier
            $table->index('is_active');                             // Filter badge aktif saja
            $table->index('sort_order');                            // Sort untuk tampilan UI
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('badges');
    }
};
