@extends('layouts.admin')

@section('header_title', 'Class Overview - TI 4A')

@section('content')
<div class="space-y-6">

    <!-- Quick Stats Grid -->
    <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
        <!-- Card 1 -->
        <div class="glass-card rounded-2xl p-6 flex items-start justify-between">
            <div>
                <p class="text-sm font-medium text-gray-500 mb-1">Mahasiswa Aktif Hari Ini</p>
                <h3 class="text-3xl font-bold text-gray-900">{{ $totalActiveStudents }} <span class="text-lg font-normal text-gray-500">/ 50</span></h3>
            </div>
            <div class="p-3 bg-blue-50 text-blue-600 rounded-xl">
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z"></path></svg>
            </div>
        </div>

        <!-- Card 2 -->
        <div class="glass-card rounded-2xl p-6 flex items-start justify-between">
            <div>
                <p class="text-sm font-medium text-gray-500 mb-1">Rata-rata Kelas (Steps)</p>
                <h3 class="text-3xl font-bold text-gray-900">{{ number_format($avgClassSteps) }}</h3>
                <p class="text-sm text-green-600 font-medium mt-1">↑ 12% dari minggu lalu</p>
            </div>
            <div class="p-3 bg-green-50 text-green-600 rounded-xl">
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6"></path></svg>
            </div>
        </div>

        <!-- Card 3 -->
        <div class="glass-card rounded-2xl p-6 flex items-start justify-between">
            <div>
                <p class="text-sm font-medium text-gray-500 mb-1">Top Performer Hari Ini</p>
                <h3 class="text-2xl font-bold text-gray-900 truncate">{{ $topPerformer['name'] }}</h3>
                <p class="text-sm text-gray-500 mt-1">{{ number_format($topPerformer['steps']) }} steps</p>
            </div>
            <div class="p-3 bg-amber-50 text-amber-600 rounded-xl">
                <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z"></path></svg>
            </div>
        </div>
    </div>

    <!-- Chart Section & Quick Actions -->
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        <!-- Bar Chart -->
        <div class="lg:col-span-2 glass-card rounded-2xl p-6">
            <div class="flex justify-between items-center mb-6">
                <h2 class="text-lg font-bold text-gray-800">Performa Mingguan Mahasiswa (Rata-rata)</h2>
                <span class="text-xs font-medium bg-gray-100 text-gray-600 px-3 py-1 rounded-full">Target: 10k/hari</span>
            </div>
            <div class="relative h-[300px] w-full">
                <canvas id="weeklyChart"></canvas>
            </div>
        </div>

        <!-- Action Panel -->
        <div class="glass-card rounded-2xl p-6 flex flex-col">
            <h2 class="text-lg font-bold text-gray-800 mb-6">Mahasiswa Zona Merah (< 50%)</h2>
            
            <div class="flex-1 overflow-y-auto pr-2 space-y-4">
                @foreach($students as $s)
                    @if($s['target_percent'] < 50)
                        <div class="flex items-center justify-between p-3 bg-red-50/50 border border-red-100 rounded-xl">
                            <div class="flex items-center gap-3">
                                <div class="w-8 h-8 rounded-full bg-red-100 text-red-600 flex items-center justify-center font-bold text-xs">
                                    {{ substr($s['name'], 0, 1) }}
                                </div>
                                <div>
                                    <p class="text-sm font-bold text-gray-900">{{ $s['name'] }}</p>
                                    <p class="text-xs text-red-600">{{ $s['target_percent'] }}% target</p>
                                </div>
                            </div>
                            <a href="{{ route('dosen.student.show', $s['id']) }}" class="text-gray-400 hover:text-gray-600">
                                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path></svg>
                            </a>
                        </div>
                    @endif
                @endforeach
            </div>

            <form action="{{ route('dosen.dashboard.notify') }}" method="POST" class="mt-4 pt-4 border-t border-gray-100">
                @csrf
                <button type="submit" class="w-full flex items-center justify-center gap-2 bg-red-50 hover:bg-red-100 text-red-600 font-medium py-2.5 px-4 rounded-xl transition-colors">
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"></path></svg>
                    Send Warning Push Notif
                </button>
            </form>
        </div>
    </div>

</div>
@endsection

@push('scripts')
<script>
    document.addEventListener('DOMContentLoaded', function() {
        const ctx = document.getElementById('weeklyChart').getContext('2d');
        
        // Data injected from Blade
        const rawData = @json($students);
        
        const labels = rawData.map(d => d.name);
        const dataPoints = rawData.map(d => d.avg_steps);
        
        // Color coding function
        const getColors = (data) => {
            return data.map(percent => {
                if (percent >= 80) return 'rgba(34, 197, 94, 0.8)'; // Green
                if (percent >= 50) return 'rgba(245, 158, 11, 0.8)'; // Yellow/Orange
                return 'rgba(239, 68, 68, 0.8)'; // Red
            });
        };

        const targetPercents = rawData.map(d => d.target_percent);
        const bgColors = getColors(targetPercents);

        new Chart(ctx, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [{
                    label: 'Rata-rata Langkah',
                    data: dataPoints,
                    backgroundColor: bgColors,
                    borderRadius: 6,
                    borderSkipped: false
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: {
                            label: function(context) {
                                return context.raw.toLocaleString() + ' langkah';
                            }
                        }
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        grid: {
                            color: 'rgba(0,0,0,0.05)',
                            drawBorder: false,
                        },
                        ticks: { color: '#94a3b8' }
                    },
                    x: {
                        grid: { display: false },
                        ticks: { color: '#64748b', font: { size: 11 } }
                    }
                }
            }
        });
    });
</script>
@endpush
