<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('user_badges', function (Blueprint $table) {
            // === PRIMARY KEY ===
            $table->uuid('id')->primary();                          // Pakai UUID, bukan composite PK — untuk kemudahan API response

            // === RELASI ===
            $table->foreignUuid('user_id')                          // FK ke users — siapa yang dapat badge
                  ->constrained('users')
                  ->onDelete('cascade');
            $table->foreignUuid('badge_id')                         // FK ke badges — badge mana yang didapat
                  ->constrained('badges')
                  ->onDelete('cascade');

            // === ACHIEVEMENT DATA ===
            $table->timestamp('earned_at');                         // Kapan badge didapatkan — bisa beda dari created_at jika retroactive
            $table->unsignedInteger('progress_value')               // Nilai progress saat badge diraih (misal 10243 steps)
                  ->nullable();                                     //   Berguna untuk tampilan "You earned this with 10,243 steps!"
            $table->boolean('is_notified')->default(false);         // Sudah di-notify ke user atau belum (cegah spam notif)

            $table->timestamps();

            // === CONSTRAINTS ===
            $table->unique(['user_id', 'badge_id']);                // 1 user hanya bisa dapat 1 badge yang sama
            $table->index('user_id');                               // Query: "semua badge milik user X"
            $table->index('earned_at');                             // Sort: badge terbaru dulu
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('user_badges');
    }
};
