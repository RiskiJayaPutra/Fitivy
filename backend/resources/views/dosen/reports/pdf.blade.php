<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <title>Laporan Aktivitas Mahasiswa</title>
    <style>
        /* Inline CSS for DomPDF support */
        body { font-family: 'Helvetica', 'Arial', sans-serif; font-size: 14px; color: #333; }
        .header { text-align: center; border-bottom: 2px solid #22c55e; padding-bottom: 20px; margin-bottom: 30px; }
        .title { font-size: 20px; font-weight: bold; margin: 0; }
        .subtitle { font-size: 14px; color: #666; margin-top: 5px; }
        table { width: 100%; border-collapse: collapse; margin-top: 20px; }
        th { background-color: #f0fdf4; color: #15803d; text-align: left; padding: 10px; border-bottom: 2px solid #dcfce7; }
        td { padding: 10px; border-bottom: 1px solid #eee; }
        .status-pass { color: #16a34a; font-weight: bold; }
        .status-fail { color: #dc2626; font-weight: bold; }
        .footer { position: absolute; bottom: 0; width: 100%; text-align: right; font-size: 12px; color: #999; border-top: 1px solid #eee; padding-top: 10px;}
    </style>
</head>
<body>

    <div class="header">
        <h1 class="title">Laporan Aktivitas Fisik Mahasiswa</h1>
        <p class="subtitle">Mata Kuliah: Pendidikan Olahraga | Kelas: TI 4A</p>
        <p class="subtitle">Periode: {{ $startDate }} s.d {{ $endDate }}</p>
    </div>

    <table>
        <thead>
            <tr>
                <th>No</th>
                <th>NIM</th>
                <th>Nama Mahasiswa</th>
                <th>Total Langkah</th>
                <th>Status</th>
            </tr>
        </thead>
        <tbody>
            @foreach($students as $index => $s)
            <tr>
                <td>{{ $index + 1 }}</td>
                <td>{{ $s['nim'] }}</td>
                <td>{{ $s['name'] }}</td>
                <td>{{ number_format($s['total_steps']) }}</td>
                <td class="{{ $s['status'] == 'Lulus' ? 'status-pass' : 'status-fail' }}">{{ $s['status'] }}</td>
            </tr>
            @endforeach
        </tbody>
    </table>

    <div class="footer">
        Dicetak otomatis oleh Fitivy System pada {{ now()->format('d M Y H:i:s') }}
    </div>

</body>
</html>
