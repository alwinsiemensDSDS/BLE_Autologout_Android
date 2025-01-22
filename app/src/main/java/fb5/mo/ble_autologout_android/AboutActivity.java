package fb5.mo.ble_autologout_android;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_about);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.about_layout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Set your name
        TextView nameTextView = findViewById(R.id.nameTextView);
        nameTextView.setText("Developer: Alwin Siemens");

        // Set up link to Android BLE Library
        TextView bleLibraryLink = findViewById(R.id.bleLibraryLink);
        bleLibraryLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openUrl("https://github.com/NordicSemiconductor/Android-BLE-Library");
            }
        });

        // Set up link to Android Scanner Compat Library
        TextView scannerLibraryLink = findViewById(R.id.scannerLibraryLink);
        scannerLibraryLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openUrl("https://github.com/NordicSemiconductor/Android-Scanner-Compat-Library");
            }
        });
    }
    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        startActivity(intent);
    }
}