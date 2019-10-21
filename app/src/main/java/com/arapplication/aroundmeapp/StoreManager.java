package com.arapplication.aroundmeapp;

import android.content.Context;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;




class StorageManager {

    private static final String TAG = StorageManager.class.getName();
    private static final String KEY_ROOT_DIR = "Anchor_ID";
    private static final String KEY_NEXT_SHORT_CODE = "next_short_code";
    private static final String KEY_PREFIX = "anchor;";
    private static final int INITIAL_SHORT_CODE = 100;
    private final DatabaseReference rootRef;
    private final DatabaseReference store;

    StorageManager(Context context) {
        FirebaseApp firebaseApp = FirebaseApp.initializeApp(context);
        rootRef = FirebaseDatabase.getInstance(firebaseApp).getReference().child(KEY_ROOT_DIR);
        store = FirebaseDatabase.getInstance(firebaseApp).getReference().child("Data");
        DatabaseReference.goOnline();
    }


    interface noteBodyListner {
        void onDataAvailable(String dataBody);
    }

    interface noteLonListner {
        void onLonAvailable(String lon);
    }

    interface noteLatListner {
        void onLatAvailable(String lat);
    }

    interface CloudAnchorIdListener {
        void onCloudAnchorIdAvailable(String cloudAnchorId);
    }

    interface ShortCodeListener {
        void onShortCodeAvailable(Integer shortCode);
    }


    /*Store node data*/
    void storeAnchor(double mLon, double mLat, String locTag, String Note){

        Log.e("Anchor ", "val "+ mLat +", "+ mLon);
        store.child(locTag).child("lon").setValue(mLon);
        store.child(locTag).child("lat").setValue(mLat);
        store.child(locTag).child("note").setValue(Note);
    }


    /*Retrieve node data*/

    void getAnchorBody(String location, noteBodyListner listner){

        store
                .child(location)
                .addListenerForSingleValueEvent(
                        new ValueEventListener() {
                            @Override
                            public void onDataChange( DataSnapshot dataSnapshot) {
                                listner.onDataAvailable(String.valueOf(dataSnapshot.child("note").getValue()));
                            }

                            @Override
                            public void onCancelled( DatabaseError databaseError) {
                                Log.d(TAG, databaseError.getMessage());
                            }
                        }
                );
    }

    void getAnchorLon(String location, noteLonListner listner){

        store
                .child(location)
                .addListenerForSingleValueEvent(
                        new ValueEventListener() {
                            @Override
                            public void onDataChange( DataSnapshot dataSnapshot) {
                                listner.onLonAvailable(String.valueOf(dataSnapshot.child("lon").getValue()));
                            }

                            @Override
                            public void onCancelled( DatabaseError databaseError) {
                                Log.d(TAG, databaseError.getMessage());
                            }
                        }
                );
    }

    void getAnchorLat(String location, noteLatListner listner){

        store
                .child(location)
                .addListenerForSingleValueEvent(
                        new ValueEventListener() {
                            @Override
                            public void onDataChange( DataSnapshot dataSnapshot) {
                                listner.onLatAvailable(String.valueOf(dataSnapshot.child("lat").getValue()));
                            }

                            @Override
                            public void onCancelled( DatabaseError databaseError) {
                                Log.d(TAG, databaseError.getMessage());
                            }
                        }
                );
    }


    /* store CLoud anchors*/
    // TODO: 9/26/19 add cloud anchor location
    void nextShortCode(ShortCodeListener listener) {

        rootRef
                .child(KEY_NEXT_SHORT_CODE)
                .runTransaction(
                        new Transaction.Handler() {
                            @Override
                            public Transaction.Result doTransaction(MutableData currentData) {
                                Integer shortCode = currentData.getValue(Integer.class);
                                if (shortCode == null) {
                                    shortCode = INITIAL_SHORT_CODE - 1;
                                }
                                currentData.setValue(shortCode + 1);
                                return Transaction.success(currentData);
                            }

                            @Override
                            public void onComplete(
                                    DatabaseError error, boolean committed, DataSnapshot currentData) {
                                if (!committed) {
                                    Log.e(TAG, "Firebase Error", error.toException());
                                    listener.onShortCodeAvailable(null);
                                } else {
                                    listener.onShortCodeAvailable(currentData.getValue(Integer.class));
                                }
                            }
                        });
    }

    void storeUsingShortCode(int shortCode, String cloudAnchorId) {
        rootRef.child(KEY_PREFIX + shortCode).setValue(cloudAnchorId);
    }

    void getCloudAnchorID(int shortCode, CloudAnchorIdListener listener) {
        rootRef
                .child(KEY_PREFIX + shortCode)
                .addListenerForSingleValueEvent(
                        new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                listener.onCloudAnchorIdAvailable(String.valueOf(dataSnapshot.getValue()));
                            }

                            @Override
                            public void onCancelled(DatabaseError error) {
                                Log.e(TAG, "etCloudAnchorID was cancelled.",
                                        error.toException());
                                listener.onCloudAnchorIdAvailable(null);
                            }
                        });
    }
}