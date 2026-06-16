@extends('layouts.admin')

@section('header_title', 'Target Management')

@section('content')
<div class="grid grid-cols-1 lg:grid-cols-2 gap-6" x-data="targetPreview()">

    <!-- Form Set Target -->
    <div class="glass-card rounded-2xl p-6">
        <h2 class="text-xl font-bold text-gray-900 mb-6">Atur Target Aktivitas Kelas</h2>

        <form action="{{ route('dosen.targets.store') }}" method="POST" class="space-y-6">
            @csrf
            
            <div>
                <label class="block text-sm font-medium text-gray-700 mb-2">Target Harian (Langkah)</label>
                <div class="flex items-center gap-4">
                    <input type="range" min="1000" max="20000" step="500" x-model="targetSteps" @input.debounce.300ms="fetchPreview" class="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer accent-fitivy-600">
                    <span class="w-20 text-right font-bold text-fitivy-700" x-text="targetSteps + ' px'"></span>
                </div>
            </div>

            <div>
                <label class="block text-sm font-medium text-gray-700 mb-2">Target Sesi Mingguan (Kali)</label>
                <input type="number" name="target_sessions" value="3" min="1" max="14" class="w-full border-gray-300 rounded-lg shadow-sm focus:border-fitivy-500 focus:ring focus:ring-fitivy-200 focus:ring-opacity-50">
            </div>

            <div class="grid grid-cols-2 gap-4">
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-2">Berlaku Mulai</label>
                    <input type="date" name="start_date" class="w-full border-gray-300 rounded-lg shadow-sm focus:border-fitivy-500 focus:ring focus:ring-fitivy-200">
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-2">Batas Waktu (Opsional)</label>
                    <input type="date" name="end_date" class="w-full border-gray-300 rounded-lg shadow-sm focus:border-fitivy-500 focus:ring focus:ring-fitivy-200">
                </div>
            </div>

            <div class="pt-4 border-t border-gray-100">
                <button type="submit" class="w-full py-3 px-4 bg-gray-900 hover:bg-gray-800 text-white font-medium rounded-xl transition-colors shadow-lg shadow-gray-900/20">
                    Terapkan Target
                </button>
            </div>
        </form>
    </div>

    <!-- Impact Preview Panel -->
    <div class="glass-card rounded-2xl p-6 relative overflow-hidden">
        <div class="absolute top-0 right-0 -mr-16 -mt-16 w-48 h-48 rounded-full bg-gradient-to-br from-fitivy-100 to-transparent opacity-50"></div>
        
        <h2 class="text-xl font-bold text-gray-900 mb-2">Impact Preview</h2>
        <p class="text-sm text-gray-500 mb-8">Berdasarkan performa kelas 30 hari terakhir, jika target <strong x-text="targetSteps"></strong> diterapkan sekarang:</p>

        <div class="relative">
            <!-- Loading overlay -->
            <div x-show="loading" class="absolute inset-0 bg-white/60 backdrop-blur-sm flex items-center justify-center rounded-xl z-10 transition-opacity">
                <svg class="animate-spin h-8 w-8 text-fitivy-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path></svg>
            </div>

            <!-- Stats -->
            <div class="grid grid-cols-2 gap-4 mb-6">
                <div class="bg-green-50 rounded-xl p-4 border border-green-100">
                    <p class="text-sm font-medium text-green-600 mb-1">Mencapai Target</p>
                    <h4 class="text-3xl font-bold text-green-700" x-text="passed + ' Mhs'"></h4>
                </div>
                <div class="bg-red-50 rounded-xl p-4 border border-red-100">
                    <p class="text-sm font-medium text-red-600 mb-1">Gagal Mencapai</p>
                    <h4 class="text-3xl font-bold text-red-700" x-text="failed + ' Mhs'"></h4>
                </div>
            </div>

            <!-- Progress Bar Preview -->
            <div>
                <div class="flex justify-between text-sm font-medium text-gray-600 mb-2">
                    <span>Passing Rate</span>
                    <span x-text="passPercent + '%'"></span>
                </div>
                <div class="w-full bg-red-100 rounded-full h-3 overflow-hidden flex">
                    <div class="bg-green-500 h-3 transition-all duration-500 ease-out" :style="'width: ' + passPercent + '%'"></div>
                </div>
            </div>
        </div>
    </div>

</div>
@endsection

@push('scripts')
<script>
    document.addEventListener('alpine:init', () => {
        Alpine.data('targetPreview', () => ({
            targetSteps: 10000,
            passed: 25,
            failed: 25,
            passPercent: 50,
            loading: false,
            
            init() {
                this.fetchPreview();
            },
            
            fetchPreview() {
                this.loading = true;
                // Fetch to TargetController@previewTarget
                fetch(`{{ route('dosen.targets.preview') }}?target_steps=${this.targetSteps}`)
                    .then(res => res.json())
                    .then(data => {
                        this.passed = data.passed;
                        this.failed = data.failed;
                        this.passPercent = data.pass_percent;
                        setTimeout(() => { this.loading = false; }, 300); // Simulate tiny delay for UX
                    })
                    .catch(err => {
                        console.error(err);
                        this.loading = false;
                    });
            }
        }))
    })
</script>
@endpush
