@extends('layouts.admin')

@section('header_title', 'Export Laporan')

@section('content')
<div class="max-w-3xl mx-auto">
    <div class="glass-card rounded-2xl p-8">
        <div class="text-center mb-8">
            <div class="w-16 h-16 bg-fitivy-50 rounded-2xl mx-auto flex items-center justify-center text-fitivy-600 mb-4 shadow-sm">
                <svg class="w-8 h-8" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path></svg>
            </div>
            <h2 class="text-2xl font-bold text-gray-900">Generate Laporan Kelas</h2>
            <p class="text-sm text-gray-500 mt-2">Unduh rekapitulasi data aktivitas fisik mahasiswa dalam format CSV (Data Mentah) atau PDF (Laporan Resmi).</p>
        </div>

        <form x-data="{ format: 'pdf' }" action="{{ route('dosen.reports.export.pdf') }}" method="GET" x-on:submit="if(format === 'csv') { $el.action = '{{ route('dosen.reports.export.csv') }}' } else { $el.action = '{{ route('dosen.reports.export.pdf') }}' }">
            <div class="grid grid-cols-2 gap-6 mb-8">
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-2">Tanggal Mulai</label>
                    <input type="date" name="start_date" value="{{ now()->subDays(30)->toDateString() }}" class="w-full border-gray-300 rounded-xl shadow-sm focus:border-fitivy-500 focus:ring focus:ring-fitivy-200">
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-2">Tanggal Akhir</label>
                    <input type="date" name="end_date" value="{{ now()->toDateString() }}" class="w-full border-gray-300 rounded-xl shadow-sm focus:border-fitivy-500 focus:ring focus:ring-fitivy-200">
                </div>
            </div>

            <div class="mb-8">
                <label class="block text-sm font-medium text-gray-700 mb-4">Format Ekspor</label>
                <div class="grid grid-cols-2 gap-4">
                    <label class="relative flex cursor-pointer rounded-xl border bg-white p-4 shadow-sm focus:outline-none" :class="format === 'pdf' ? 'border-fitivy-500 ring-1 ring-fitivy-500' : 'border-gray-300'">
                        <input type="radio" name="format" value="pdf" x-model="format" class="sr-only" aria-labelledby="format-pdf">
                        <span class="flex flex-1">
                            <span class="flex flex-col">
                                <span class="block text-sm font-medium text-gray-900">Dokumen PDF</span>
                                <span class="mt-1 flex items-center text-sm text-gray-500">Laporan resmi berlogo, cocok untuk arsip Kaprodi.</span>
                            </span>
                        </span>
                        <svg class="h-5 w-5 text-fitivy-600" :class="format === 'pdf' ? 'block' : 'hidden'" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"></path></svg>
                    </label>

                    <label class="relative flex cursor-pointer rounded-xl border bg-white p-4 shadow-sm focus:outline-none" :class="format === 'csv' ? 'border-fitivy-500 ring-1 ring-fitivy-500' : 'border-gray-300'">
                        <input type="radio" name="format" value="csv" x-model="format" class="sr-only" aria-labelledby="format-csv">
                        <span class="flex flex-1">
                            <span class="flex flex-col">
                                <span class="block text-sm font-medium text-gray-900">Data CSV</span>
                                <span class="mt-1 flex items-center text-sm text-gray-500">Data mentah untuk diolah di Excel atau SPSS.</span>
                            </span>
                        </span>
                        <svg class="h-5 w-5 text-fitivy-600" :class="format === 'csv' ? 'block' : 'hidden'" fill="currentColor" viewBox="0 0 20 20"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"></path></svg>
                    </label>
                </div>
            </div>

            <button type="submit" class="w-full flex justify-center py-3 px-4 border border-transparent rounded-xl shadow-sm text-sm font-bold text-white bg-fitivy-600 hover:bg-fitivy-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-fitivy-500 transition-colors">
                Download Laporan
            </button>
        </form>
    </div>
</div>
@endsection
