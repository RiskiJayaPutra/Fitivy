<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('class_user', function (Blueprint $table) {
            // === COMPOSITE KEY ===
            $table->foreignUuid('class_id')                         // FK ke classes
                  ->constrained('classes')
                  ->onDelete('cascade');                             // Kelas dihapus → enrollment ikut hilang
            $table->foreignUuid('user_id')                          // FK ke users (mahasiswa)
                  ->constrained('users')
                  ->onDelete('cascade');                             // User dihapus → enrollment ikut hilang

            $table->timestamp('enrolled_at')->useCurrent();         // Kapan mahasiswa didaftarkan ke kelas
            $table->timestamps();

            // === CONSTRAINTS ===
            $table->primary(['class_id', 'user_id']);               // Composite PK: 1 mahasiswa tidak bisa enroll 2x di kelas sama
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('class_user');
    }
};
