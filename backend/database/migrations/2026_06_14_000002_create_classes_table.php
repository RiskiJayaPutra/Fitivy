<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('classes', function (Blueprint $table) {
            // === PRIMARY KEY ===
            $table->uuid('id')->primary();

            // === CLASS INFO ===
            $table->string('name');                                 // Nama kelas, misal "Olahraga A 2026"
            $table->string('code', 20)->unique();                   // Kode kelas unik, misal "OLR-A-2026" — dipakai di QR
            $table->text('description')->nullable();                // Deskripsi opsional tentang kelas
            $table->string('semester', 10);                         // Semester, misal "2025/2026-2"
            $table->string('academic_year', 9);                     // Tahun akademik, misal "2025/2026"

            // === DOSEN PENGAMPU ===
            $table->foreignUuid('lecturer_id')                      // FK ke users — dosen yang mengampu kelas ini
                  ->constrained('users')
                  ->onDelete('cascade');                             // Jika dosen dihapus, kelas ikut terhapus

            // === STATUS ===
            $table->boolean('is_active')->default(true);            // False = kelas sudah selesai/arsip

            $table->timestamps();

            // === INDEXES ===
            $table->index('lecturer_id');                           // Query: "semua kelas milik dosen X"
            $table->index('semester');                               // Filter by semester untuk reporting
            $table->index('is_active');                             // Filter kelas aktif saja
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('classes');
    }
};
