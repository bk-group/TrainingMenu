package com.zalo.trainingmenu.vrsample;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.adobe.internal.xmp.XMPIterator;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.properties.XMPPropertyInfo;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.xmp.XmpDirectory;
import com.ldt.menulayout.ui.AbsLocaleActivity;
import com.ldt.vrview.VRView;
import com.ldt.vrview.model.VRPhoto;
import com.zalo.trainingmenu.App;
import com.zalo.trainingmenu.R;
import com.zalo.trainingmenu.downloader.ui.base.OptionBottomSheet;
import com.zalo.trainingmenu.util.PreferenceUtil;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;

import es.dmoral.toasty.Toasty;

public class VrSampleActivity extends AbsLocaleActivity {
    private static final String TAG = "VrSampleActivity";

    public static final String ACTION_VIEW_NEWS_FEED = "view_news_feed";
    public static final String EXTRA_VR_NEWS_FEED = "vr_news_feed";
    public static final String ACTION_PICK_PHOTO_FROM_GALLERY = "ACTION_PICK_PHOTO_FROM_GALLERY";

    public static final String ACTION_VIEW_SAMPLE = "action_view_example";
    public static final String ACTION_VIEW_FROM_GALLERY = "action_view_from_gallery";
    public static final int REQUEST_CODE_PICK_FROM_GALLERY = 1;

    private VRView mVRView;
    private View mFullScreenView;

    private void buildLayout(ViewGroup root) {
        if(mFullScreenView==null) {
            mFullScreenView = LayoutInflater.from(this).inflate(R.layout.fullscreen_vr_layout,root,false);
            mFullScreenView.findViewById(R.id.back_button).setOnClickListener((v) -> finish());
            mVRView.setOnClickListener(v -> {
                if(mFullScreenView.getVisibility()==View.VISIBLE)
                mFullScreenView.animate().alpha(0).withEndAction(() -> mFullScreenView.setVisibility(View.GONE)).start();
                else mFullScreenView.animate().alpha(1).withStartAction(() -> mFullScreenView.setVisibility(View.VISIBLE)).start();
            });

            View menuButton = mFullScreenView.findViewById(R.id.menu_button);
            if(menuButton!=null) menuButton.setOnClickListener(v -> showOption());

            View pickButton = mFullScreenView.findViewById(R.id.pick_image_button);
            if(pickButton!=null) pickButton.setOnClickListener(v -> pickFromGallery());

            root.addView(mFullScreenView);
        }
    }


    private void updateMode(int mode) {
        if(mode!=mMode) {
            mMode = mode;
            if (mMode == MODE_SAMPLE) {
                mFullScreenView.findViewById(R.id.sample_group).setVisibility(View.VISIBLE);
                mFullScreenView.findViewById(R.id.pick_image_button).setVisibility(View.GONE);

            } else if (mMode == MODE_GALLERY) {
                mFullScreenView.findViewById(R.id.sample_group).setVisibility(View.GONE);
                mFullScreenView.findViewById(R.id.pick_image_button).setVisibility(View.VISIBLE);
            }

            if(mFullScreenView.getVisibility()!=View.VISIBLE)
                mFullScreenView.animate().alpha(1).withStartAction(() -> mFullScreenView.setVisibility(View.VISIBLE)).start();
        }
    }

    private void pickFromGallery() {
        executeWriteStorageAction(new Intent(ACTION_PICK_PHOTO_FROM_GALLERY));
    }

    @Override
    public void onRequestPermissionsResult(Intent intent, int permissionType, boolean granted) {
        super.onRequestPermissionsResult(intent, permissionType, granted);
        if(intent!=null&&ACTION_PICK_PHOTO_FROM_GALLERY.equals(intent.getAction())&&granted) {
            try {
               Intent i = new Intent(Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(i, REQUEST_CODE_PICK_FROM_GALLERY);
            } catch (Exception e) {
                Toasty.error(this,"Something went wrong!").show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==REQUEST_CODE_PICK_FROM_GALLERY && resultCode== Activity.RESULT_OK&&data!=null) {
            setVRPhotoWithUri(data.getData());
        }
    }

    private float[] getAreaAnglesFromMetadata(String path) {
        InputStream inputStream;
        String message = "";
        try {
            inputStream = new FileInputStream(path);
        } catch (Exception e) {
            inputStream = null;
            message = "Exception: "+ e.getMessage();
        }

        if(message.isEmpty()) {
            if (inputStream != null) {
                try {
                    Metadata metadata = ImageMetadataReader.readMetadata(inputStream);
                    for (XmpDirectory xmpDirectory : metadata.getDirectoriesOfType(XmpDirectory.class)) {
                        XMPMeta xmpMeta = xmpDirectory.getXMPMeta();
                        XMPIterator itr = xmpMeta.iterator();
                        boolean isEquirectangular = false;
                        boolean isPropertiesValid = true;
                        float[]  properties = new float[] {-1,-1,-1,-1, -1,-1}; // full-width, full-height, left, top, width, height

                        String name;
                        String value;
                        while (itr.hasNext()) {
                            XMPPropertyInfo property = (XMPPropertyInfo) itr.next();
                            Log.d(TAG, "property: " + property.getPath() + " => " + property.getValue());
                            name = property.getPath();
                            value = property.getValue();
                            if(name!=null&&!name.isEmpty()&&value!=null&&!value.isEmpty())
                            switch (name) {
                                case VRPhoto.GPANO_PROJECTION_TYPE:
                                    isEquirectangular = "equirectangular".equals(value);
                                    break;
                                case VRPhoto.GPANO_FULL_PANO_WIDTH_PIXELS:
                                    properties[0] = Float.parseFloat(value);
                                    break;
                                case VRPhoto.GPANO_FULL_PANO_HEIGHT_PIXELS:
                                    properties[1] = Float.parseFloat(value);
                                    break;
                                case VRPhoto.GPANO_CROPPED_AREA_LEFT_PIXELS:
                                    properties[2] = Float.parseFloat(value);
                                    break;
                                case VRPhoto.GPANO_CROPPED_AREA_TOP_PIXELS:
                                    properties[3] = Float.parseFloat(value);
                                    break;
                                case VRPhoto.GPANO_CROPPED_AREA_IMAGE_WIDTH_PIXELS:
                                    properties[4] = Float.parseFloat(value);
                                    break;
                                case VRPhoto.GPANO_CROPPED_AREA_IMAGE_HEIGHT_PIXELS:
                                    properties[5] = Float.parseFloat(value);
                            }
                        }

                        for (float p: properties) {
                            if(p<0) {
                                isPropertiesValid = false;
                                break;
                            }
                        }

                        if(isEquirectangular&&isPropertiesValid) {
                            // valid
                            float[] areaAngles = new float[4]; // left, top, width, height in degree angle
                            areaAngles[0] = (properties[2] / properties[0])*360;
                            areaAngles[1] = (properties[3] / properties[1])*180;
                            areaAngles[2] = (properties[4] / properties[0])*360;
                            areaAngles[3] = (properties[5] / properties[1])*180;
                            return areaAngles;
                        }
                    }
                } catch (Exception e) {
                    message = "Exception: "+e.getMessage();
                }
            } else message = "InputStream is null";
        }

        Log.d(TAG,"get metadata with message: ["+message+"]");
        return null;
    }

    private void setVRPhotoWithPath(String path) {
        boolean valid = true;
        Bitmap bitmap = null;
        float[] area = null;
        if(path==null) valid = false;
        else {
            area = getAreaAnglesFromMetadata(path);
            try {
                bitmap = BitmapFactory.decodeFile(path);
            } catch (Exception ignored) {
            }
        }
        if(bitmap==null) valid = false;
        if(!valid)
            Toasty.error(App.getInstance(),"Image is unavailable").show();
        else {
            if(area!=null&&area.length>=4)
            Log.d(TAG, "set vr photo with area :"+area[0]+", "+area[1]+", "+area[2]+", "+area[3]);
            else Log.d(TAG, "set vr photo with null area");
            mVRView.setVRPhoto(VRPhoto.with(this).setBitmap(bitmap).setAreaAngles(area).get());
            PreferenceUtil.getInstance().saveVRSource(path);
        }
    }

    private void setVRPhotoWithUri(Uri data) {
        boolean valid = true;
        if(data==null) valid = false;
        String path = null;
        if(valid)
        try {
            String[] fileCols = {MediaStore.Images.Media.DATA};
            Cursor c = getContentResolver().query(data,fileCols,null,null,null);
            if(c!=null) {
                c.moveToFirst();
                int index = c.getColumnIndex(fileCols[0]);
                path = c.getString(index);
               // Log.d(TAG, "onActivityResult: " + path);
                c.close();
            }
        } catch (Exception e) {
            valid = false;
        }

        if(!valid) setVRPhotoWithPath(null);
        else {
            // valid
            setVRPhotoWithPath(path);
        }
    }

    private int[] mSampleOptionMenu = new int[] {
            R.string.warning_divider,
            R.string.sample_mode,
            R.string.normal,
            R.string.gallery_chooser_mode
    };

    private int[] mGalleryOptionMenu = new int[] {
            R.string.sample_mode,
            R.string.warning_divider,
            R.string.gallery_chooser_mode
    };

    public static final int MODE_SAMPLE = 0;
    public static final int MODE_GALLERY = 1;

    private int mMode = MODE_SAMPLE;

    void showOption() {
        int[] options = (mMode==MODE_SAMPLE) ? mSampleOptionMenu : mGalleryOptionMenu;
        OptionBottomSheet.newInstance(options, new OptionBottomSheet.CallBack() {
            @Override
            public boolean onOptionClicked(int optionID) {
                switch (optionID) {
                    case R.string.sample_mode:
                        updateMode(MODE_SAMPLE);
                        break;
                    case R.string.gallery_chooser_mode:
                        updateMode(MODE_GALLERY);
                        break;
                }
                return true;
            }
        }).show(getSupportFragmentManager(),"OPTION_MENU");
    };


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.vr_layout);
        mVRView = findViewById(R.id.vr_view);
        buildLayout(findViewById(R.id.root));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR );

        Intent intent = getIntent();
        if(intent != null && ACTION_VIEW_NEWS_FEED.equals(intent.getAction())) {
            // view news feed
            VRNewsFeed newsFeed = intent.getParcelableExtra(EXTRA_VR_NEWS_FEED);


            if(newsFeed!=null) {
                if(mFullScreenView!=null) {
                    ((TextView)mFullScreenView.findViewById(R.id.author_text_view)).setText(newsFeed.mAuthor);
                    ((TextView)mFullScreenView.findViewById(R.id.description_text_view)).setText(newsFeed.mDescription);
                }

                AsyncTask.execute(() -> {
                    newsFeed.mVRPhoto = createPhoto(newsFeed.mDrawableID);
                    mVRView.post(()->mVRView.setVRPhoto(newsFeed.getVRPhoto()));
                });
            }

        } else {
            int mode;
            if(intent!=null&&ACTION_VIEW_FROM_GALLERY.equals(intent.getAction())) {
                mode = MODE_GALLERY;
            } else mode = MODE_SAMPLE;
            updateMode(mode);
            buildSample();
        }
    }

    ArrayList<VRPhoto> mVRPhotos;
    private int mCurrentPos = 0;

    private void setVRPhotoSample(int posInExample) {
        if(posInExample!=-1) {
            mVRView.setVRPhoto(mVRPhotos.get(posInExample));
            PreferenceUtil.getInstance().setSavedDepthPhoto("sample_"+posInExample);
        }
        else mVRView.setVRPhoto(null);
    }

    private void buildSample() {
        mVRView.setOnLongClickListener(v -> {
            if(!mVRPhotos.isEmpty()&&mMode==MODE_SAMPLE) {
                mCurrentPos++;
                if(mCurrentPos == mVRPhotos.size()) setVRPhotoSample(-1);
                else {
                    if(mCurrentPos == mVRPhotos.size()+1) mCurrentPos = 0;
                    setVRPhotoSample(mCurrentPos);
                }
            }
            return true;
        });

        if(mVRPhotos==null) mVRPhotos = new ArrayList<>();
        AsyncTask.execute(() -> {
            mVRPhotos.add(createPhoto(R.drawable._360sp));
            mVRPhotos.add(createPhoto(R.drawable._360x));
            mVRPhotos.add(createPhoto(R.drawable.rural));
            mVRPhotos.add(createPhoto(R.drawable.down1, new float[]{0,0,320,180}));
            mVRPhotos.add(createPhoto(R.drawable.down2, new float[]{0,30,360,120}));
            mVRView.post(() -> {
                String savedVR = PreferenceUtil.getInstance().getSavedVRSource();
                Log.d(TAG, "saved: "+savedVR);
                if(savedVR==null) setVRPhotoSample(0);

                else
                switch (savedVR) {
                    case "sample_0":
                        setVRPhotoSample(0);
                    break;

                    case "sample_1":
                        setVRPhotoSample(1);
                    break;

                    case "sample_2":
                        setVRPhotoSample(2);
                    break;

                    case "sample_3":
                        setVRPhotoSample(3);
                    break;

                    case "sample_4":
                        setVRPhotoSample(4);
                    break;

                    case "sample_5":
                        setVRPhotoSample(5);
                        break;

                    default:
                        setVRPhotoWithPath(savedVR);
                }
            });
        });
    }

    private VRPhoto createPhoto(int resId) {
        return createPhoto(resId, VRPhoto.getDefaultAngleAreas());
    }

        private VRPhoto createPhoto(int resId, float[] area) {
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeResource(getResources(),resId);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return VRPhoto.with(this).setBitmap(bitmap).setAreaAngles(area).get();
    }

    @Override
    protected int title() {
        return R.string.vr_sample;
    }

    @Override
    protected void onResume() {
       super.onResume();
       mVRView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mVRView.onPause();
    }
}
