package com.arapplication.aroundmeapp;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import uk.co.appoly.arcorelocation.LocationMarker;
import uk.co.appoly.arcorelocation.LocationScene;
import uk.co.appoly.arcorelocation.rendering.LocationNode;
import uk.co.appoly.arcorelocation.rendering.LocationNodeRender;

import static java.lang.Math.round;

public class MainActivity extends AppCompatActivity {

    private CustomArFragment fragment;
    private Anchor cloudAnchor;

    private enum AppAnchorState {
        NONE,
        HOSTING,
        HOSTED,
        RESOLVING,
        RESOLVED
    }

    private AppAnchorState appAnchorState = AppAnchorState.NONE;

    private SnackbarHelper snackbarHelper = new SnackbarHelper();

    private StorageManager storageManager;

    private ViewRenderable createViewRenderable;

    private ViewRenderable retrieveViewRendarable;

    private Boolean retrieveState = false;

    private LocationScene locationScene;

    private double longitude;
    private double latitude;

    private double mLongitudeNode;
    private double mLatitudeNode;

    private String locationTagString;

    private String noteDataString;
    private String mNoteBody;

    private final int LOCATION_PERMISSION = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fragment = (CustomArFragment) getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);
        fragment.getPlaneDiscoveryController().hide();
        fragment.getArSceneView().getScene().setOnUpdateListener(this::onUpdate);


        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]   {Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION);
            return;
        }
        Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        longitude = location.getLongitude();
        latitude = location.getLatitude();


        Button clearButton = findViewById(R.id.clear_button);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setCloudAnchor(null);
                retrieveState = false;
            }
        });

        Button resolveButton = findViewById(R.id.find_button);
        resolveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (cloudAnchor != null){
                    snackbarHelper.showMessageWithDismiss(getParent(), "Please Press clear");
                    return;
                }

               /*
               //Retrieve anchor by ID

                ResolveDialogFragment dialog = new ResolveDialogFragment();
                dialog.setOkListener(MainActivity.this::onResolveOkPressed);
                dialog.show(getSupportFragmentManager(), "Resolve");

                */

                placeRetrievedNote();

            }
        });

        fragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {

                    if (plane.getType() != Plane.Type.HORIZONTAL_UPWARD_FACING ||
                            appAnchorState != AppAnchorState.NONE){
                        return;
                    }

                    Anchor newAnchor = fragment.getArSceneView().getSession().hostCloudAnchor(hitResult.createAnchor());

                    setCloudAnchor(newAnchor);

                    appAnchorState = AppAnchorState.HOSTING;
                    snackbarHelper.showMessage(this, "Now Hosting Your Note...");

                    placeNewNote(fragment, cloudAnchor, Uri.parse("Fox.sfb"));

                }
        );

        storageManager = new StorageManager(this);
    }

    private void onUpdate(FrameTime frameTime) {
        if (retrieveState) {

            if (locationScene == null) {
                locationScene = new LocationScene(this, this, fragment.getArSceneView());
                LocationMarker layoutLocationMarker = new LocationMarker(
                        mLongitudeNode,
                        mLatitudeNode,
                        placeRetrieveNode()
                );

                layoutLocationMarker.setRenderEvent(new LocationNodeRender() {
                    @Override
                    public void render(LocationNode node) {
                        View eView = retrieveViewRendarable.getView();
                        TextView dataTextView = eView.findViewById(R.id.textView);
                        TextView distanceTextView = eView.findViewById(R.id.textView2);

                        dataTextView.setText(mNoteBody);
                        distanceTextView.setText(round(calculateDistance(latitude, longitude, mLatitudeNode, mLongitudeNode)) + " Meters");
                    }
                });

                locationScene.mLocationMarkers.add(layoutLocationMarker);

            }


            Frame frame = fragment.getArSceneView().getArFrame();


            if (locationScene != null) {
                locationScene.processFrame(frame);
            }
        }

        checkUpdatedAnchor();
    }

    private void setCloudAnchor (Anchor newAnchor){
        if (cloudAnchor != null){
            cloudAnchor.detach();
        }

        cloudAnchor = newAnchor;
        appAnchorState = AppAnchorState.NONE;
        snackbarHelper.hide(this);
    }

    private synchronized void checkUpdatedAnchor(){
        if (appAnchorState != AppAnchorState.HOSTING && appAnchorState != AppAnchorState.RESOLVING){
            return;
        }
        Anchor.CloudAnchorState cloudState = cloudAnchor.getCloudAnchorState();
        if (appAnchorState == AppAnchorState.HOSTING) {
            if (cloudState.isError()) {
                snackbarHelper.showMessageWithDismiss(this, "Error. Please try again "
                        + cloudState);
                appAnchorState = AppAnchorState.NONE;
            } else if (cloudState == Anchor.CloudAnchorState.SUCCESS) {
                storageManager.nextShortCode((shortCode) -> {
                    if (shortCode == null){
                        snackbarHelper.showMessageWithDismiss(this, "Error");
                        return;
                    }
                    storageManager.storeUsingShortCode(shortCode, cloudAnchor.getCloudAnchorId());

                    snackbarHelper.showMessageWithDismiss(this, "Note Hosted! ");
                });

                appAnchorState = AppAnchorState.HOSTED;
            }
        }

        else if (appAnchorState == AppAnchorState.RESOLVING){
            if (cloudState.isError()) {
                snackbarHelper.showMessageWithDismiss(this, "Error resolving Note... "
                        + cloudState);
                appAnchorState = AppAnchorState.NONE;
            } else if (cloudState == Anchor.CloudAnchorState.SUCCESS){
                snackbarHelper.showMessageWithDismiss(this, "Note resolved successfully");
                appAnchorState = AppAnchorState.RESOLVED;
            }
        }



    }

    private void placeNewNote(ArFragment fragment, Anchor anchor, Uri model) {

        getUserLocation();

         ModelRenderable.builder()
                .setSource(fragment.getContext(), model)
                .build()
                .thenAccept(renderable -> addNodeToScene(fragment, anchor, renderable))
                .exceptionally((throwable -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage(throwable.getMessage())
                            .setTitle("Error!");
                    AlertDialog dialog = builder.create();
                    dialog.show();
                    return null;
                }));


        ViewRenderable.builder()
                .setView(this, R.layout.create_layout)
                .build()
                .thenAccept(renderable -> createViewRenderable = renderable);



    }

    private void addNodeToScene(ArFragment fragment, Anchor anchor, Renderable renderable) {

        AnchorNode anchorNode = new AnchorNode(anchor);
        TransformableNode node = new TransformableNode(fragment.getTransformationSystem());
        node.setRenderable(renderable);
        node.setParent(anchorNode);
        fragment.getArSceneView().getScene().addChild(anchorNode);

        TransformableNode nameView = new TransformableNode(fragment.getTransformationSystem());
        nameView.setLocalPosition(new Vector3(0f,0.5f,0) );
        nameView.setRenderable(createViewRenderable);
        nameView.setParent(anchorNode);
        nameView.select();

    }

    private void getUserLocation() {


        final EditText inputloc = new EditText(this);

        AlertDialog.Builder builderLoc = new AlertDialog.Builder(this);
        builderLoc
                .setTitle("Enter Your Location")
                .setView(inputloc)
                .setPositiveButton(
                        "OK",
                        (dialog, which) -> {
                            Editable locData = inputloc.getText();
                            locationTagString = locData.toString();

                            getUserNote(locationTagString);
                        })
                .setNegativeButton("Cancel", (dialog, which) -> {
                });

        builderLoc.show();


    }

    private void getUserNote(String locationTag){

        View eView = createViewRenderable.getView();

        TextView noteTextView = eView.findViewById(R.id.textView1);

        final EditText input = new EditText(this);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder
                .setTitle("Enter Your Note")
                .setView(input)
                .setPositiveButton(
                        "OK",
                        (dialog, which) -> {
                            Editable shortCodeText = input.getText();
                            noteDataString = shortCodeText.toString();
                            noteTextView.setText(shortCodeText);

                            storageManager.storeAnchor(longitude,latitude,locationTag, noteDataString);
                        })
                .setNegativeButton("Cancel", (dialog, which) -> {
                });

        builder.show();

    }

    private void placeRetrievedNote() {

        ViewRenderable.builder()
                .setView(this, R.layout.retrieve_layout)
                .build()
                .thenAccept(renderable -> retrieveViewRendarable = renderable);

        setUserNoteData();
    }

    private Node placeRetrieveNode() {
        Node base = new Node();
        base.setRenderable(retrieveViewRendarable);
        return base;
    }

    private void setUserNoteData(){


        final EditText inputloc = new EditText(this);

        AlertDialog.Builder builderLoc = new AlertDialog.Builder(this);
        builderLoc
                .setTitle("Enter Your Location")
                .setView(inputloc)
                .setPositiveButton(
                        "OK",
                        (dialog, which) -> {
                            Editable locData = inputloc.getText();
                            locationTagString = locData.toString();

                            storageManager
                                    .getAnchorBody(locationTagString,(dataBody)->{
                                        mNoteBody = dataBody;

                                        Log.e("Data_Received", "Body: " + mNoteBody);
                                    });
                            storageManager
                                    .getAnchorLat(locationTagString,(dataLat)->{
                                        mLatitudeNode = Double.parseDouble(dataLat);

                                        Log.e("Data_Received", "Lat: " + mLatitudeNode);
                                    });
                            storageManager
                                    .getAnchorLon(locationTagString,(dataLon)->{
                                        mLongitudeNode = Double.parseDouble(dataLon);

                                        Log.e("Data_Received", "Lon:  " + mLongitudeNode);
                                    });

                            retrieveState = true;

                        })
                .setNegativeButton("Cancel", (dialog, which) -> {
                });

        builderLoc.show();


    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2){

        double distD;

        if ((lat1 == lat2) && (lon1 == lon2)) {
            return 0;
        }
        else {
            double theta = lon1 - lon2;
            double dist = Math.sin(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2)) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(theta));
            double distA = Math.acos(dist);
            double distB = Math.toDegrees(distA);
            double distC = distB * 60 * 1.1515;
            distD = distC * 1.609344;

            Log.e("Distance ", "calculation :" + distD);
            return  distD;
        }
    }


    @Override
    public void onPause() {
        super.onPause();

        if (locationScene != null) {
            locationScene.pause();
        }

        fragment.getArSceneView().pause();
    }



    /* //Get cloud anchor by ID

    private void onResolveOkPressed(String dialogValue){

        Integer shortCode =  Integer.parseInt(dialogValue);
        storageManager.getCloudAnchorID(shortCode,(cloudAnchorId) -> {
            Anchor resolvedAnchor = fragment.getArSceneView().getSession().resolveCloudAnchor(cloudAnchorId);
            setCloudAnchor(resolvedAnchor);
            placeNewNote(fragment, cloudAnchor, Uri.parse("Fox.sfb"), false);
            snackbarHelper.showMessage(this, "Now Resolving Anchor...");
            appAnchorState = AppAnchorState.RESOLVING;
        });

    }*/


    /*private void onUpdateFrame(FrameTime frameTime){
        checkUpdatedAnchor();
    }*/

}
