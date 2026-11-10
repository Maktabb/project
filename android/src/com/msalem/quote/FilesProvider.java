package com.msalem.quote;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Serves files written to cache/share so WhatsApp and other apps can read them. */
public class FilesProvider extends ContentProvider {
    static File shareDir(android.content.Context c){ File d=new File(c.getCacheDir(),"share"); d.mkdirs(); return d; }
    private File fileFor(Uri uri) throws FileNotFoundException {
        String name=uri.getLastPathSegment();
        if(name==null||name.contains("/")||name.contains("..")) throw new FileNotFoundException();
        File f=new File(shareDir(getContext()),name);
        if(!f.exists()) throw new FileNotFoundException(name);
        return f;
    }
    @Override public boolean onCreate(){ return true; }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(fileFor(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] proj,String sel,String[] args,String sort){
        MatrixCursor c=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});
        try{ File f=fileFor(uri); c.addRow(new Object[]{f.getName(),f.length()}); }catch(Exception e){}
        return c;
    }
    @Override public String getType(Uri uri){ return MainActivity.mimeFor(uri.getLastPathSegment()); }
    @Override public Uri insert(Uri u,ContentValues v){ return null; }
    @Override public int delete(Uri u,String s,String[] a){ return 0; }
    @Override public int update(Uri u,ContentValues v,String s,String[] a){ return 0; }
}
