<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('users', function (Blueprint $table) {
            // === PRIMARY KEY ===
            $table->uuid('id')->primary();                          // UUID agar aman untuk sync offline & tidak predictable

            // === IDENTITAS ===
            $table->string('name');                                 // Nama lengkap mahasiswa/dosen
            $table->string('email')->unique();                      // Email unik untuk login (email kampus)
            $table->timestamp('email_verified_at')->nullable();     // Null = belum verifikasi email
            $table->string('password');                             // Bcrypt hashed password
            $table->string('nim_nip', 20)->nullable()->unique();    // NIM (mahasiswa) atau NIP (dosen), nullable untuk admin

            // === ROLE & ACCESS ===
            $table->enum('role', [                                  // Role menentukan akses fitur:
                'mahasiswa',                                        //   mahasiswa = tracking & personal dashboard
                'dosen',                                            //   dosen = monitoring kelas & report
                'admin_prodi',                                      //   admin_prodi = manajemen prodi
                'super_admin'                                       //   super_admin = full access semua fitur
            ])->default('mahasiswa');
            $table->boolean('is_active')->default(true);            // Soft-disable tanpa delete, misal mahasiswa lulus

            // === PROFILE ===
            $table->string('avatar_url')->nullable();               // URL foto profil (stored di Firebase Storage)
            $table->float('height_cm')->nullable();                 // Tinggi badan — untuk kalkulasi kalori (BMR)
            $table->float('weight_kg')->nullable();                 // Berat badan — untuk kalkulasi kalori (BMR)
            $table->date('birth_date')->nullable();                 // Tanggal lahir — untuk kalkulasi heart rate zone
            $table->enum('gender', ['male', 'female'])->nullable(); // Gender — faktor dalam formula kalori Mifflin-St Jeor

            // === DEVICE & NOTIFICATION ===
            $table->string('fcm_token')->nullable();                // Firebase Cloud Messaging token untuk push notification
            $table->string('device_id')->nullable();                // Android device ID — validasi 1 akun = 1 device
            $table->string('device_model')->nullable();             // Model HP — untuk debug sensor compatibility

            // === LARAVEL DEFAULTS ===
            $table->rememberToken();                                // "Remember me" token untuk web session
            $table->timestamps();                                   // created_at, updated_at

            // === INDEXES ===
            $table->index('role');                                  // Filter cepat by role (dashboard admin, report dosen)
            $table->index('is_active');                             // Filter user aktif saja
            $table->index(['role', 'is_active']);                   // Composite: "semua mahasiswa aktif"
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('users');
    }
};
