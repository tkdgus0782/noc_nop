
package org.pytorch.demo.objectdetection;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class SelectDetect extends AppCompatActivity implements Runnable {//c언어 main이라 생각하면 됩니다ㅏ
    private String modelName = "yolov5s.torchscript";
    private String metaName = "classes.txt";

    private int mImageIndex = 0;
    private String[] mTestImages = {"test1.png", "test2.jpg", "test3.png"};//테스트 이미지 셋. 나중에는 live빼고는 다 없애지 않을까 싶음

    private ImageView mImageView;
    private ResultView mResultView;
    private Button mButtonDetect;
    private ProgressBar mProgressBar;
    private Bitmap mBitmap = null;//이미지를 비트맵으로 변환 후, 프로세싱함
    private Module mModule = null;//이미지 모델 로드
    private float mImgScaleX, mImgScaleY, mIvScaleX, mIvScaleY, mStartX, mStartY;//이미지 프로세싱

    protected class backgroundThread extends Thread{
        protected boolean isRun = true;
    }

    public static String assetFilePath(Context context, String assetName) throws IOException {//asset   파일들의 위치 경로를 설정해줌. 건들x
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {//객체 생성
        super.onCreate(savedInstanceState);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }

        //권한 관련

        setContentView(R.layout.activity_select);//

        try {
            mBitmap = BitmapFactory.decodeStream(getAssets().open(mTestImages[mImageIndex]));
        } catch (IOException e) {
            Log.e("Object Detection", "Error reading assets", e);
            finish();
        }//테스트이미지를 읽어서 비트맵으로 변환

        mImageView = findViewById(R.id.imageView);//테스트 이미지 뷰
        mImageView.setImageBitmap(mBitmap);
        mResultView = findViewById(R.id.resultView);//테스트 이미지에 박스 라벨링한 결과 뷰
        mResultView.setVisibility(View.INVISIBLE);

        final Button buttonTest = findViewById(R.id.testButton);//테스트이미지 123 옮기는 버튼인데, 나중에는 아마 삭제 하지 않으띾
        buttonTest.setText(("Test Image 1/3"));
        buttonTest.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mResultView.setVisibility(View.INVISIBLE);
                mImageIndex = (mImageIndex + 1) % mTestImages.length;
                buttonTest.setText(String.format("Text Image %d/%d", mImageIndex + 1, mTestImages.length));

                try {
                    mBitmap = BitmapFactory.decodeStream(getAssets().open(mTestImages[mImageIndex]));
                    mImageView.setImageBitmap(mBitmap);
                } catch (IOException e) {
                    Log.e("Object Detection", "Error reading assets", e);
                    finish();
                }
            }
        });


        final Button buttonSelect = findViewById(R.id.selectButton);//이미지 셀렉트하는 버튼인데 이것도 아마 삭제하지 않을까. live만 남길듯
        buttonSelect.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mResultView.setVisibility(View.INVISIBLE);

                final CharSequence[] options = { "Choose from Photos", "Take Picture", "Cancel" };
                AlertDialog.Builder builder = new AlertDialog.Builder(SelectDetect.this);
                builder.setTitle("New Test Image");

                builder.setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int item) {
                        if (options[item].equals("Take Picture")) {
                            Intent takePicture = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                            startActivityForResult(takePicture, 0);
                        }
                        else if (options[item].equals("Choose from Photos")) {
                            Intent pickPhoto = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI);
                            startActivityForResult(pickPhoto , 1);
                        }
                        else if (options[item].equals("Cancel")) {
                            dialog.dismiss();
                        }
                    }
                });
                builder.show();
            }
        });


        mButtonDetect = findViewById(R.id.detectButton);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);//프로세싱 중일대 뜨는 로딩 바

        mButtonDetect.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mButtonDetect.setEnabled(false);
                mProgressBar.setVisibility(ProgressBar.VISIBLE);
                mButtonDetect.setText(getString(R.string.run_model));

                mImgScaleX = (float)mBitmap.getWidth() / PrePostProcessor.mInputWidth;
                mImgScaleY = (float)mBitmap.getHeight() / PrePostProcessor.mInputHeight;

                mIvScaleX = (mBitmap.getWidth() > mBitmap.getHeight() ? (float)mImageView.getWidth() / mBitmap.getWidth() : (float)mImageView.getHeight() / mBitmap.getHeight());
                mIvScaleY  = (mBitmap.getHeight() > mBitmap.getWidth() ? (float)mImageView.getHeight() / mBitmap.getHeight() : (float)mImageView.getWidth() / mBitmap.getWidth());

                mStartX = (mImageView.getWidth() - mIvScaleX * mBitmap.getWidth())/2;
                mStartY = (mImageView.getHeight() -  mIvScaleY * mBitmap.getHeight())/2;

                //이미지 비율을 맞추기 위해서 몇배로 조정해야하는지부

                //Fork생각하심 됩니다.
                Thread thread = new Thread(SelectDetect.this);
                thread.start();
            }
        });

        try {
            mModule = LiteModuleLoader.load(SelectDetect.assetFilePath(getApplicationContext(), modelName));//
            BufferedReader br = new BufferedReader(new InputStreamReader(getAssets().open(metaName)));
            //에셋 파일에 추가한 모델 + 모델에 학습된 클래스명들을 순서대로 \n으로 구분해서 같이 투입. 내부적으론 숫자로 관리하니까 무조건 txt도 넣어줘야함
            String line;
            List<String> classes = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                classes.add(line);
            }
            PrePostProcessor.mClasses = new String[classes.size()];
            classes.toArray(PrePostProcessor.mClasses);
            //여기를 잘 조정하면 yaml같은것도 가능인데 귀찮고 굳이 그렇게 해야할 이유도 없어보이니 그냥 txt로 만들어주셈
        } catch (IOException e) {
            Log.e("Object Detection", "Error reading assets", e);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_CANCELED) {
            switch (requestCode) {
                case 0:
                    if (resultCode == RESULT_OK && data != null) {
                        mBitmap = (Bitmap) data.getExtras().get("data");//카메라에 찍힌 걸 비트맵으로 변환
                        Matrix matrix = new Matrix();
                        matrix.postRotate(90.0f);//90도 회전 시켜주는 행렬. 아마 보통 카메라는 가로가 더 넓은 비율이니, 폰에서 쓸 수 있게 90도 돌려주는 걸로 예상함
                        mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
                        mImageView.setImageBitmap(mBitmap);
                    }
                    break;
                case 1://이부분은 미리 찍혀있는 사진관련ㅇ 인듯.
                    if (resultCode == RESULT_OK && data != null) {
                        Uri selectedImage = data.getData();
                        String[] filePathColumn = {MediaStore.Images.Media.DATA};
                        if (selectedImage != null) {
                            Cursor cursor = getContentResolver().query(selectedImage,
                                    filePathColumn, null, null, null);
                            if (cursor != null) {
                                cursor.moveToFirst();
                                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                                String picturePath = cursor.getString(columnIndex);
                                mBitmap = BitmapFactory.decodeFile(picturePath);
                                Matrix matrix = new Matrix();
                                matrix.postRotate(90.0f);
                                mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
                                mImageView.setImageBitmap(mBitmap);
                                cursor.close();
                            }
                        }
                    }
                    break;
            }
        }
    }

    @Override
    public void run() {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(mBitmap, PrePostProcessor.mInputWidth, PrePostProcessor.mInputHeight, true);//리사이즈
        final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(resizedBitmap, PrePostProcessor.NO_MEAN_RGB, PrePostProcessor.NO_STD_RGB);
        //저번에 말한 int16, float8 변환으로 용량 줄이기 하려면 여기랑 prepostprocessor 만져야하는데 굳이 안해도 좋을듯
        IValue[] outputTuple = mModule.forward(IValue.from(inputTensor)).toTuple();
        //실제로 모델이 예측을 수행하는 문장
        final Tensor outputTensor = outputTuple[0].toTensor();
        final float[] outputs = outputTensor.getDataAsFloatArray();
        final ArrayList<Result> results =  PrePostProcessor.outputsToNMSPredictions(outputs, mImgScaleX, mImgScaleY, mIvScaleX, mIvScaleY, mStartX, mStartY);
        //모델이 예측한 것을 박스라벨링으로 바꿔주는 문장

        runOnUiThread(() -> {//그냥 ui
            mButtonDetect.setEnabled(true);
            mButtonDetect.setText(getString(R.string.detect));
            mProgressBar.setVisibility(ProgressBar.INVISIBLE);
            mResultView.setResults(results);
            mResultView.invalidate();
            mResultView.setVisibility(View.VISIBLE);
        });
    }
}
