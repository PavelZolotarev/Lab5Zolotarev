package com.example.laba5;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings; // Импорт для Settings
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import android.content.pm.PackageManager;
import androidx.core.content.FileProvider;
import android.content.ActivityNotFoundException;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_MANAGE_EXTERNAL_STORAGE = 100;
    private static final int REQUEST_PERMISSION = 101;

    private static final String PREFS_NAME = "MyAppPrefs";
    private static final String SHOW_POPUP_KEY = "showPopup";

    private EditText journalIdInput;
    private Button downloadButton, viewButton, deleteButton;
    private String filePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        journalIdInput = findViewById(R.id.journalIdInput);
        downloadButton = findViewById(R.id.downloadButton);
        viewButton = findViewById(R.id.viewButton);
        deleteButton = findViewById(R.id.deleteButton);

        downloadButton.setOnClickListener(v -> checkPermissionsAndDownload());
        viewButton.setOnClickListener(v -> openFile());
        deleteButton.setOnClickListener(v -> deleteFile());

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean showPopup = prefs.getBoolean(SHOW_POPUP_KEY, true);
    }

    private void checkPermissionsAndDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                downloadFile();
            } else {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, REQUEST_MANAGE_EXTERNAL_STORAGE);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
            } else {
                downloadFile();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MANAGE_EXTERNAL_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    downloadFile();
                } else {
                    Toast.makeText(this, "Разрешение на доступ ко всем файлам не предоставлено", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                downloadFile();
            } else {
                Toast.makeText(this, "Разрешение на запись во внешнее хранилище не предоставлено", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void downloadFile() {
        String journalId = journalIdInput.getText().toString().trim();
        String url = "https://ntv.ifmo.ru/file/journal/" + journalId + ".pdf";

        RequestQueue queue = Volley.newRequestQueue(this);
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                },
                error -> {
                    if (error.networkResponse != null) {
                        int statusCode = error.networkResponse.statusCode;
                        Log.e("NetworkError", "Status Code: " + statusCode);
                        if (statusCode == 404) {
                            Toast.makeText(this, "Файл не найден", Toast.LENGTH_SHORT).show();
                        } else if (statusCode >= 500) {
                            Toast.makeText(this, "Ошибка сервера: " + statusCode, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Ошибка загрузки: " + statusCode, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e("NetworkError", "Ошибка сети: " + error.getMessage());
                        Toast.makeText(this, "Ошибка сети", Toast.LENGTH_SHORT).show();
                    }
                }) {
            @Override
            protected Response<String> parseNetworkResponse(com.android.volley.NetworkResponse response) {
                String contentType = response.headers.get("Content-Type");
                if (contentType != null && contentType.equals("application/pdf")) {
                    saveFile(response.data, journalId);
                } else {
                    Toast.makeText(MainActivity.this, "Файл не является PDF", Toast.LENGTH_SHORT).show();
                }
                return super.parseNetworkResponse(response);
            }
        };

        queue.add(stringRequest);
    }

    private void saveFile(byte[] data, String journalId) {
        new Thread(() -> {
            try {
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File file = new File(downloadsDir, journalId + ".pdf");

                FileOutputStream outputStream = new FileOutputStream(file);
                outputStream.write(data);
                outputStream.close();

                runOnUiThread(() -> {
                    filePath = file.getAbsolutePath();
                    Log.d("FilePath", "Файл сохранен по пути: " + filePath);
                    viewButton.setVisibility(View.VISIBLE);
                    deleteButton.setVisibility(View.VISIBLE);
                    viewButton.setEnabled(true);
                    deleteButton.setEnabled(true);
                    Toast.makeText(this, "Файл загружен в Загрузки", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e("DownloadError", "Ошибка сохранения файла: " + e.getMessage());
            }
        }).start();
    }

    private void openFile() {
        File file = new File(filePath);
        if (file.exists()) {
            Uri fileUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "Нет приложения для просмотра PDF", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e("OpenFileError", "Ошибка при открытии файла: " + e.getMessage());
                Toast.makeText(this, "Ошибка при открытии файла", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Файл не найден", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteFile() {
        File file = new File(filePath);
        if (file.exists() && file.delete()) {
            Toast.makeText(this, "Файл удален", Toast.LENGTH_SHORT).show();
            viewButton.setVisibility(View.GONE);
            deleteButton.setVisibility(View.GONE);
            viewButton.setEnabled(false);
            deleteButton.setEnabled(false);
        } else {
            Toast.makeText(this, "Ошибка удаления файла", Toast.LENGTH_SHORT).show();
        }
    }
}
