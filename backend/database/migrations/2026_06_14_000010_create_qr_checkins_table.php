<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('qr_checkins', function (Blueprint $table) {
            // === PRIMARY KEY ===
            $table->uuid('id')->primary();

            // === RELASI ===
            $table->foreignUuid('user_id')                          // FK ke users — mahasiswa yang check-in
                  ->constrained('users')
                  ->onDelete('cascade');
            $table->foreignUuid('class_id')                         // FK ke classes — kelas mana yang di-check-in
                  ->constrained('classes')
                  ->onDelete('cascade');
            $table->foreignUuid('verified_by')                      // FK ke users — dosen yang generate QR / verifikasi
                  ->nullable()
                  ->constrained('users')
                  ->onDelete('set null');

            // === QR DATA ===
            $table->string('qr_token', 64)->unique();              // Token unik dalam QR code — SHA-256 hash
            $table->timestamp('checked_in_at');                     // Waktu check-in (dari device)
            $table->timestamp('qr_valid_from');                     // QR berlaku mulai kapan (di-set dosen)
            $table->timestamp('qr_valid_until');                    // QR berlaku sampai kapan — expired = reject

            // === LOCATION VERIFICATION ===
            $table->decimal('latitude', 10, 7)->nullable();         // Latitude saat check-in — validasi lokasi
            $table->decimal('longitude', 10, 7)->nullable();        // Longitude saat check-in — validasi lokasi
            $table->decimal('location_accuracy', 6, 2)->nullable(); // Akurasi GPS (meter) — reject jika >100m

            // === STATUS ===
            $table->enum('status', [                                // Status check-in:
                'valid',                                            //   valid = berhasil terverifikasi
                'expired',                                          //   expired = QR sudah kadaluarsa
                'invalid_location',                                 //   invalid_location = lokasi terlalu jauh
                'duplicate'                                         //   duplicate = sudah check-in sebelumnya
            ])->default('valid');

            $table->timestamps();

            // === INDEXES ===
            $table->index('user_id');                               // Query: "semua check-in milik user X"
            $table->index('class_id');                              // Query: "semua check-in di kelas Y"
            $table->index(['user_id', 'class_id']);                 // Composite: "check-in user X di kelas Y"
            $table->index('checked_in_at');                         // Sort by waktu check-in
            $table->index('status');                                // Filter by status (hanya valid)
            $table->index(['class_id', 'checked_in_at']);           // Composite: "attendance report kelas Y hari ini"
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('qr_checkins');
    }
};
