package se.dsektionen.dcide;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity implements RadioGroup.OnCheckedChangeListener{

    private final static int PERMISSION_CAMERA = 1;
    private Session currentSession;
    String[] PERMISSIONS = {Manifest.permission.CAMERA, Manifest.permission.NFC};

    NFCForegroundUtil nfcForegroundUtil = null;

    private TextView resultOkView;
    private TextView resultFailView;
    private boolean isInRegistrationMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        nfcForegroundUtil = new NFCForegroundUtil(this);
        NfcAdapter mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        resultFailView = (TextView) findViewById(R.id.resultFailView);
        resultOkView = (TextView) findViewById(R.id.resultOkView);
        RadioGroup actionGroup = (RadioGroup) findViewById(R.id.action_group);
        actionGroup.setOnCheckedChangeListener(this);


        currentSession = null;


        if (mNfcAdapter == null) {
            Toast.makeText(this, "Den här appen kräver en enhet med NFC.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_CAMERA);
        } else {
            Intent intent = new Intent(this,QRActivity.class);
            startActivityForResult(intent,2);
        }

    }


    @Override
    protected void onResume() {
        super.onResume();
        nfcForegroundUtil.enableForeground();

        if (!nfcForegroundUtil.getNfc().isEnabled())
        {
            Toast.makeText(getApplicationContext(), "Aktivera NFC och tryck på tillbaka.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        nfcForegroundUtil.disableForeground();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d("NFC","Intent received");
        getTagInfo(intent);
    }

    private void getTagInfo(Intent intent) {
        Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        v.vibrate(30);
        String rfid = bin2int(tag.getId());
        ResultHandler handler = new ResultHandler() {
            @Override
            public void onResult(final String response,final int status) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showResult(response,status);
                    }
                });
            }
        };

        if(isInRegistrationMode){
            RequestUtils.registerUser(currentSession, rfid, handler);
        } else {
            RequestUtils.deleteUser(currentSession, rfid, handler);
        }

    }


    private void showResult(String message, int status){
        if(status == RequestUtils.STATUS_OK){
            resultOkView.setText(message);
            resultOkView.setVisibility(View.VISIBLE);
            resultOkView.postDelayed(new Runnable() { public void run() { resultOkView.setVisibility(View.GONE); resultOkView.setText(""); } }, 1500);

        } else {
            resultFailView.setText(message);
            resultFailView.setVisibility(View.VISIBLE);
            resultFailView.postDelayed(new Runnable() { public void run() { resultFailView.setVisibility(View.GONE); resultFailView.setText(""); } }, 1500);

        }
    }

    static String bin2int(byte[] data) {
        byte[] reverse = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            reverse[data.length-i-1] = data[i];
        }
        ByteBuffer bb = ByteBuffer.wrap(reverse);


        return Integer.toString(bb.getInt());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(resultCode == RESULT_OK && data.hasCategory("QR")){
            try {
                JSONObject sessionJSON = new JSONObject(data.getStringExtra("QRresult"));
                currentSession = new Session(sessionJSON.getString("session_id"),sessionJSON.getString("admin_token"));
            }catch (JSONException e){
                AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
                dialogBuilder.setMessage("Inte en giltig QR-kod. Försök igen.");
                dialogBuilder.setPositiveButton("Okej", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Intent intent = new Intent(getApplicationContext(),QRActivity.class);
                        intent.putExtra("noresult",true);
                        startActivityForResult(intent,2);
                    }
                });
                dialogBuilder.show();
            }
        } else if(data == null){
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
            dialogBuilder.setMessage("Vänligen skanna sessionens QR-kod för att registrera användare.");
            dialogBuilder.setPositiveButton("Okej", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    Intent intent = new Intent(getApplicationContext(),QRActivity.class);
                    intent.putExtra("noresult",true);
                    startActivityForResult(intent,2);
                }
            });
            dialogBuilder.show();

        }

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case PERMISSION_CAMERA:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    Intent intent = new Intent(this,QRActivity.class);
                    startActivityForResult(intent,2);
                }
        }

    }

    @Override
    public void onCheckedChanged(RadioGroup radioGroup, int id) {
        isInRegistrationMode = id == R.id.radioAdd;
    }
}