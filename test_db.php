<?php
$host = "localhost";
$username = "wash7823_fitivy";
$password = "fitivysehatselalu";
$database = "wash7823_fitivy";
$port = 3306;

// Create connection
$conn = new mysqli($host, $username, $password, $database, $port);

// Check connection
if ($conn->connect_error) {
    die("Connection failed: " . $conn->connect_error);
}
echo "Connected successfully to database $database on $host:$port\n";
?>
