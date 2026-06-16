@extends('layouts.admin')

@section('header_title', 'Detail Mahasiswa')

@section('content')
<div class="space-y-6">

    <!-- Header Card -->
    <div class="glass-card rounded-2xl p-6 flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div class="flex items-center gap-4">
            <div class="w-16 h-16 rounded-full bg-red-100 text-red-600 flex items-center justify-center font-bold text-2xl">
                {{ substr($student['name'], 0, 1) }}
            </div>
            <div>
                <h2 class="text-2xl font-bold text-gray-900">{{ $student['name'] }}</h2>
                <p class="text-sm text-gray-500">NIM: {{ $student['nim'] }} &bull; Rata-rata: {{ $student['avg_steps'] }} langkah</p>
            </div>
        </div>
        
        <div class="flex items-center gap-3" x-data="{ noteModal: false }">
            <span class="px-3 py-1 bg-red-100 text-red-700 text-sm font-semibold rounded-full">
                Zona {{ $student['status'] }} ({{ $student['target_percent'] }}%)
            </span>
            <button @click="noteModal = true" class="px-4 py-2 bg-fitivy-600 hover:bg-fitivy-700 text-white text-sm font-medium rounded-xl transition-colors">
                Tambah Catatan
            </button>

            <!-- Modal Note -->
            <div x-show="noteModal" style="display: none;" class="fixed inset-0 z-50 overflow-y-auto" aria-labelledby="modal-title" role="dialog" aria-modal="true">
                <div class="flex items-end justify-center min-h-screen pt-4 px-4 pb-20 text-center sm:block sm:p-0">
                    <div x-show="noteModal" x-transition.opacity class="fixed inset-0 bg-gray-500 bg-opacity-75 transition-opacity" @click="noteModal = false"></div>
                    <span class="hidden sm:inline-block sm:align-middle sm:h-screen">&#8203;</span>
                    <div x-show="noteModal" x-transition class="inline-block align-bottom bg-white rounded-2xl text-left overflow-hidden shadow-xl transform transition-all sm:my-8 sm:align-middle sm:max-w-lg sm:w-full">
                        <form action="{{ route('dosen.student.note', $student['id']) }}" method="POST">
                            @csrf
                            <div class="bg-white px-4 pt-5 pb-4 sm:p-6 sm:pb-4">
                                <h3 class="text-lg leading-6 font-medium text-gray-900 mb-4" id="modal-title">Tambah Catatan Evaluasi</h3>
                                <textarea name="note" rows="4" class="w-full border-gray-300 rounded-lg shadow-sm focus:border-fitivy-500 focus:ring focus:ring-fitivy-200 focus:ring-opacity-50" placeholder="Tulis catatan performa mahasiswa..."></textarea>
                            </div>
                            <div class="bg-gray-50 px-4 py-3 sm:px-6 sm:flex sm:flex-row-reverse gap-2">
                                <button type="submit" class="w-full inline-flex justify-center rounded-xl border border-transparent shadow-sm px-4 py-2 bg-fitivy-600 text-base font-medium text-white hover:bg-fitivy-700 focus:outline-none sm:w-auto sm:text-sm">Simpan</button>
                                <button type="button" @click="noteModal = false" class="mt-3 w-full inline-flex justify-center rounded-xl border border-gray-300 shadow-sm px-4 py-2 bg-white text-base font-medium text-gray-700 hover:bg-gray-50 focus:outline-none sm:mt-0 sm:w-auto sm:text-sm">Batal</button>
                            </div>
                        </form>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        
        <!-- Timeline -->
        <div class="glass-card rounded-2xl p-6">
            <h3 class="text-lg font-bold text-gray-800 mb-6">Timeline 30 Hari Terakhir</h3>
            <div class="relative border-l border-gray-200 ml-3 space-y-6">
                @foreach($timeline as $t)
                <div class="mb-8 ml-6">
                    <span class="absolute flex items-center justify-center w-6 h-6 bg-fitivy-100 rounded-full -left-3 ring-8 ring-white">
                        <svg class="w-3 h-3 text-fitivy-600" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z" clip-rule="evenodd"></path></svg>
                    </span>
                    <h4 class="flex items-center mb-1 text-md font-semibold text-gray-900">{{ ucfirst($t['type']) }}</h4>
                    <time class="block mb-2 text-sm font-normal leading-none text-gray-400">{{ $t['date'] }}</time>
                    <p class="text-sm font-normal text-gray-500">{{ $t['steps'] }} langkah &bull; {{ $t['duration'] }} &bull; {{ $t['calories'] }} kcal</p>
                </div>
                @endforeach
            </div>
        </div>

        <!-- GPS Route Map -->
        <div class="glass-card rounded-2xl p-6 flex flex-col h-[500px]">
            <h3 class="text-lg font-bold text-gray-800 mb-4">Visualisasi Rute Terbaru</h3>
            <div id="map" class="flex-1 w-full rounded-xl z-0"></div>
            <p class="text-xs text-gray-400 mt-2 text-center">Rute direkam pada sesi terakhir.</p>
        </div>

    </div>

</div>
@endsection

@push('scripts')
<script>
    document.addEventListener('DOMContentLoaded', function() {
        // Initialize Leaflet Map
        const routeData = @json($routes);
        
        if(routeData && routeData.length > 0) {
            const map = L.map('map').setView(routeData[0], 15);
            
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                attribution: '&copy; OpenStreetMap contributors'
            }).addTo(map);

            const polyline = L.polyline(routeData, {color: '#16a34a', weight: 4}).addTo(map);
            map.fitBounds(polyline.getBounds());
            
            // Add Start and End Markers
            L.circleMarker(routeData[0], {color: 'blue', radius: 5}).addTo(map).bindPopup('Start');
            L.circleMarker(routeData[routeData.length - 1], {color: 'red', radius: 5}).addTo(map).bindPopup('Finish');
        } else {
            document.getElementById('map').innerHTML = "<div class='flex items-center justify-center h-full bg-gray-100 text-gray-500 rounded-xl'>Tidak ada data GPS untuk ditampilkan.</div>";
        }
    });
</script>
@endpush
