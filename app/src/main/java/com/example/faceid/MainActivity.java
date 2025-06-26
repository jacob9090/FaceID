package com.example.faceid;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.normal.TedPermission;
import com.example.faceid.mobilefacenet.MobileFaceNet;
import com.example.faceid.mtcnn.MTCNN;

import org.opencv.android.CameraBridgeViewBase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private MTCNN mtcnn;
    private MobileFaceNet mfn;
    private ImageView imageView;
    public TextView textView;

    private Uri currentphotoUri;
    private File currentphotoFile;
    private String timeStamp;

    private static final String APP_LOCATION = "faceRecognition";
    private static final String FILE_PROVIDER_PATH = "com.example.faceid.fileprovider";
    private static final int REGISTER_CAMERA_REQUEST_CODE = 100;
    private static final int REGISTER_GALLERY_REQUEST_CODE = 101;
    private static final int RECOGNIZE_CAMERA_REQUEST_CODE = 102;
    private static final int RECOGNIZE_GALLERY_REQUEST_CODE = 103;
    public static int CAMERA_INDEX = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Button btn_register = findViewById(R.id.btn_register);
        Button btn_recognize = findViewById(R.id.btn_recognize);
        Button btn_show_faces = findViewById(R.id.btn_show_faces);
        imageView = findViewById(R.id.imageView);
        textView = findViewById(R.id.textView);

        if (!checkPermissions()) requestPermissions();

        start();
        try {
            mtcnn = new MTCNN(getAssets());
            mfn = new MobileFaceNet(getAssets());
        } catch (IOException e) {
            e.printStackTrace();
        }

        btn_register.setOnClickListener(v -> {
            if (checkPermissions()) take_photo(MainActivity.this, 0, "register");
            else requestPermissions();
        });

        btn_recognize.setOnClickListener(v -> {
            if (checkPermissions()) take_photo(MainActivity.this, 2, "recognize");
            else requestPermissions();
        });

        btn_show_faces.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, ShowRegisteredFaces.class)));
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED);
    }

    private void requestPermissions() {
        PermissionListener permissionListener = new PermissionListener() {
            @Override
            public void onPermissionGranted() {
                Toast.makeText(MainActivity.this, "Permission Granted", Toast.LENGTH_SHORT).show();
                start();
            }

            @Override
            public void onPermissionDenied(List<String> deniedPermissions) {
                Toast.makeText(MainActivity.this, "Permission Denied", Toast.LENGTH_SHORT).show();
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            TedPermission.create()
                    .setPermissionListener(permissionListener)
                    .setPermissions(Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES)
                    .check();
        } else {
            TedPermission.create()
                    .setPermissionListener(permissionListener)
                    .setPermissions(Manifest.permission.CAMERA,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .check();
        }
    }

    private void start() {
        File folder = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES) + "/" + APP_LOCATION + "/faces");
        if (!folder.exists() && folder.mkdir()) {
            Toast.makeText(this, "App folder created...", Toast.LENGTH_LONG).show();
        }
    }

    private void take_photo(Context context, final int reference, String module) {
        final CharSequence[] options_reg = {"Take Photo", "Choose from Gallery", "Cancel"};
        final CharSequence[] options_rec = {"Run on Live Camera", "Take Photo", "Choose from Gallery", "Cancel"};
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Choose a photo");

        if (module.equals("register")) {
            builder.setItems(options_reg, (dialog, item) -> {
                if (options_reg[item].equals("Take Photo")) takeCameraPhoto(reference);
                else if (options_reg[item].equals("Choose from Gallery")) startActivityForResult(new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI), reference + 1);
                else dialog.dismiss();
            });
        } else {
            builder.setItems(options_rec, (dialog, item) -> {
                if (options_rec[item].equals("Run on Live Camera")) run_live_camera();
                else if (options_rec[item].equals("Take Photo")) takeCameraPhoto(reference);
                else if (options_rec[item].equals("Choose from Gallery")) startActivityForResult(new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI), reference + 1);
                else dialog.dismiss();
            });
        }
        builder.show();
    }

    private void takeCameraPhoto(final int requestCode) {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            try {
                timeStamp = DateFormat.getDateTimeInstance().format(new Date());
                File storageDir = getExternalFilesDir(Environment.DIRECTORY_PODCASTS);
                File image = File.createTempFile("JPEG_" + timeStamp + "_", ".jpg", storageDir);
                currentphotoUri = FileProvider.getUriForFile(this, FILE_PROVIDER_PATH, image);
                currentphotoFile = image;
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentphotoUri);
                startActivityForResult(takePictureIntent, requestCode);
            } catch (IOException e) {
                Log.e("IOException", "File not read", e);
            }
        }
    }

    private void run_live_camera() {
        final CharSequence[] options = {"Front Facing Camera", "Back Camera"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Camera View");
        builder.setCancelable(false);
        builder.setItems(options, (dialog, item) -> {
            CAMERA_INDEX = options[item].equals("Front Facing Camera") ? CameraBridgeViewBase.CAMERA_ID_FRONT : CameraBridgeViewBase.CAMERA_ID_BACK;
            startActivity(new Intent(MainActivity.this, CameraActivity.class));
        });
        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_CANCELED) {
            if (requestCode == 0 || requestCode == 2) request_name(currentphotoUri, requestCode == 0 ? "camera" : "recognize");
            else if (data != null) {
                Uri selectedImage = data.getData();
                if (requestCode == 1) request_name(selectedImage, "gallery");
                else if (requestCode == 3) recognize_face(selectedImage, "gallery");
            }
        }
    }

    private void request_name(final Uri uri, final String module) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Person Name");
        builder.setMessage("Enter the person's name you want to register. (else face will not be registered)");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        final EditText input = new EditText(this);
        layout.addView(input);
        builder.setView(layout);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String text = input.getText().toString();
            if (!text.equals("")) register_face(uri, module, text);
            else delete_temp();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void register_face(Uri uri, String module, String person_name) {
        try {
            Bitmap bitmap = Helper.handleSamplingAndRotationBitmap(this, uri);
            imageView.setImageBitmap(bitmap);
            bitmap = Helper.detect_and_crop_face(this, mtcnn, bitmap, textView);
            if (bitmap != null) {
                File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                File cropped = new File(storageDir, person_name + ".jpg");
                FileOutputStream out = new FileOutputStream(cropped);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                out.flush(); out.close();
                Toast.makeText(this, "Face registered successfully", Toast.LENGTH_SHORT).show();
                if (module.equals("camera")) delete_temp();
            }
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
        }
    }

    private void recognize_face(Uri uri, String module) {
        try {
            Bitmap new_image = Helper.handleSamplingAndRotationBitmap(this, uri);
            imageView.setImageBitmap(new_image);
            new_image = Helper.detect_and_crop_face(this, mtcnn, new_image, textView);
            File registered_faces_path = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (new_image != null && registered_faces_path.exists()) {
                for (File face_data : registered_faces_path.listFiles()) {
                    Bitmap registered_face = BitmapFactory.decodeFile(face_data.getAbsolutePath());
                    float _score = mfn.compare(registered_face, new_image);
                    if (_score > MobileFaceNet.THRESHOLD) {
                        textView.setTextColor(Color.GREEN);
                        textView.setText(face_data.getName().split("\\.")[0]);
                        break;
                    } else {
                        textView.setTextColor(Color.RED);
                        textView.setText("Unknown Person");
                    }
                }
                if (module.equals("camera")) delete_temp();
            }
        } catch (Exception e) {
            Log.e("Exception", e.getMessage());
        }
    }

    private void delete_temp() {
        try {
            if (currentphotoFile.delete() && currentphotoFile.exists()) {
                currentphotoFile.getCanonicalFile().delete();
                if (currentphotoFile.exists()) getApplicationContext().deleteFile(currentphotoFile.getName());
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }
}