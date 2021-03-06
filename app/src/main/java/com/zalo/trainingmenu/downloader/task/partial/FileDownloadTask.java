package com.zalo.trainingmenu.downloader.task.partial;

import android.os.Build;
import android.util.Log;
import android.webkit.URLUtil;

import com.zalo.trainingmenu.downloader.base.BaseTask;
import com.zalo.trainingmenu.downloader.base.BaseTaskController;
import com.zalo.trainingmenu.downloader.base.Task;
import com.zalo.trainingmenu.downloader.database.DownloadDBHelper;
import com.zalo.trainingmenu.downloader.model.DownloadItem;
import com.zalo.trainingmenu.downloader.model.PartialInfo;
import com.zalo.trainingmenu.downloader.model.TaskInfo;
import com.zalo.trainingmenu.util.Util;

import java.io.Closeable;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

public class FileDownloadTask extends BaseTask<PartialTaskController> {
    private static final String TAG = "FileDownloadTask";

    public FileDownloadTask(final int id, PartialTaskController manager, DownloadItem item) {
        super(id, manager, item);
    }

    private FileDownloadTask(int id, PartialTaskController taskManager, String directory, String url, long createdTime, String fileTitle, boolean isAutoGeneratedPath, boolean isAutoGeneratedTitle) {
        super(id, taskManager, directory, url, createdTime, fileTitle, isAutoGeneratedTitle, isAutoGeneratedPath);
    }

    public ArrayList<PartialDownloadTask> getPartialDownloadTasks() {
        return mPartialDownloadTasks;
    }

    private final ArrayList<PartialDownloadTask> mPartialDownloadTasks = new ArrayList<>();

    public static FileDownloadTask restoreInstance(PartialTaskController taskManager, TaskInfo info) {
        FileDownloadTask task = new FileDownloadTask(info.getId(), taskManager,info.getDirectory(),info.getURLString(),info.getCreatedTime(),info.getFileTitle(), info.isAutogeneratedTitle(), info.isAutogeneratedPath());

        int state = info.getState();
        if(state==BaseTask.RUNNING) state = PAUSED;
        task.setState(state);
        task.setDownloadedInBytes(info.getDownloadedInBytes());
        task.setFileContentLength(info.getFileContentLength());
        task.restoreProgress(info.getProgress());
        task.setMessage(info.getMessage());
        task.setFinishedTime(info.getFinishedTime());
        task.restoreFirstExecutedTime(info.getFirstExecutedTime());
        task.restoreLastExecutedTime(info.getLastExecutedTime());
        task.setRunningTime(info.getRunningTime());

        task.mPartialDownloadTasks.clear();
        List<PartialInfo> partialInfoList = info.getPartialInfoList();
        for (PartialInfo partialInfo:
        partialInfoList) {
            PartialDownloadTask partialTask = PartialDownloadTask.restoreInstance(task, partialInfo.getId(),partialInfo.getStartByte(),partialInfo.getEndByte(),partialInfo.getState(),partialInfo.getDownloadedInBytes());
            task.mPartialDownloadTasks.add(partialTask);
        }

        return task;
    }

    @Override
    public void runTask() {
        downloadFile();
    }

    private void downloadFile() {
        if(isStopByUser()) return;


        switch (getMode()) {
            case EXECUTE_MODE_NEW_DOWNLOAD:
            case EXECUTE_MODE_RESTART:
                setFileContentLength(0);
                setDownloadedInBytes(0);
                restoreProgress(0);
                mPartialDownloadTasks.clear();
            case EXECUTE_MODE_RESUME:

                setState(CONNECTING);
                notifyTaskChanged();
             /*   try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }*/
                connectThenDownload();
                break;
        }
    }

    public void fillUpProperties(URL url) {
        // Request for File Content Length

        // Request for File Title if unavailable title name

        // Request for Directory Path if unavailable path folder

        HttpURLConnection requestConnection = null;

        try {
            if(URLUtil.isHttpsUrl(url.toString()))
                // Create HTTPS Connection
                requestConnection = (HttpsURLConnection)url.openConnection();
            // Else create HTTP Connection
            else requestConnection = (HttpURLConnection)url.openConnection();

            requestConnection.setRequestMethod("HEAD");

            long fileContentLength= -1;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                fileContentLength = requestConnection.getContentLengthLong();
            } else {

                String sizeString = requestConnection.getHeaderField(CONTENT_LENGTH);

                if (!sizeString.isEmpty())
                try {
                    fileContentLength = Long.parseLong(sizeString);
                } catch (NumberFormatException ignored) {}
            }

            if(fileContentLength<=0) fileContentLength = -1;
            setFileContentLength(fileContentLength);

            if(getMode()==BaseTask.EXECUTE_MODE_NEW_DOWNLOAD) {
                String contentDisposition = requestConnection.getHeaderField("Content-Disposition");
                Log.d(TAG, "content disposition = " + contentDisposition);

                String contentType = requestConnection.getContentType();
                // String guessExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType(requestConnection.getContentType());
                // Log.d(TAG, "guess this file with extension is "+guessExtension);
                if (isAutogeneratedTitle())
                    setFileTitle(Util.generateTitle(getURLString(), getDirectory(), contentDisposition, contentType));
            }

        } catch (IOException ignored) {} finally {
            if (requestConnection != null) {
                requestConnection.disconnect();
            }
        }
    }


    private void connectThenDownload() {

        /*
            Tạo kết nối
        */

        URL url;
        try {
            url = new URL(getURLString());
        } catch (MalformedURLException e) {
            url = null;
        }

        if(url==null) {
            setState(FAILURE_TERMINATED, "URL is null");
            notifyTaskChanged();
            return;
        }

        /*
            Bắt đầu tải và ghi file
        */

        fillUpProperties(url);
        notifyTaskChanged();
        Log.d(TAG, "start download file size = "+ getFileContentLength());

        /*
            Kiểm tra action dismiss từ người dùng
         */

        if(isStopByUser()) {
            return;
        }


        /*
            Chuyển trạng thái sang đang chạy
         */
        initSpeed();
        setState(RUNNING);
        notifyTaskChanged();
        Log.d(TAG, "FileTask id"+getId()+" is running");

        // create new Partial Download Task
        // only the first running
        if(mPartialDownloadTasks.isEmpty()) {
            mPartialDownloadTasks.clear();
            long fileSize = getFileContentLength();
            if(!isProgressSupport()) {
                mPartialDownloadTasks.add(new PartialDownloadTask(this, (int)DownloadDBHelper.getInstance().generateNewPartialTaskId(0,-1)));
                Log.d(TAG, "file task id "+getId()+" does not support progress");
            } else {
                int numberConnections = getConnectionNumber();
                long usualPartSize = fileSize / numberConnections;

                long startByte ;
                long endByte;

                for (int i = 0; i < numberConnections; i++) {
                    startByte = usualPartSize*i;
                    endByte = (i!= numberConnections -1) ? (i+1)*usualPartSize - 1 : fileSize - 1;

                    Log.d(TAG, "file task id "+ getId()+" is creating new partial task "+i+" to download with "+ startByte+" to "+ endByte);
                    PartialDownloadTask partialTask = new PartialDownloadTask(this,  (int)DownloadDBHelper.getInstance().generateNewPartialTaskId(startByte,endByte),startByte,endByte );
                    mPartialDownloadTasks.add(partialTask);
                }
            }
        }

        Log.d(TAG, "executing "+ mPartialDownloadTasks.size()+" partial download task");

        for (int i = 0; i < mPartialDownloadTasks.size(); i++) {
            mPartialDownloadTasks.get(i).execute();
        }

        for (int i = 0; i < mPartialDownloadTasks.size(); i++) {
            mPartialDownloadTasks.get(i).waitMeFinish();
        }

        // Check if state is running and all partial tasks were successful
        boolean success = true;
        for (PartialDownloadTask task :
                mPartialDownloadTasks) {
            if(task.getState()!=BaseTask.SUCCESS) {
                Log.d(TAG, "check success: partial task id "+task.getId() +" is "+ Task.getStateName(task.getState()));
                success = false;
                break;
            }
        }

        // if success, set success state and notify it
        if(success) {
            setState(BaseTask.SUCCESS);
        } else switch (getState()) {
            case BaseTask.CONNECTING:
            case BaseTask.PENDING:
            case BaseTask.RUNNING:
                setState(BaseTask.FAILURE_TERMINATED);
                break;
            default:
                Log.d(TAG, "check success: failed and task state now is "+ Task.getStateName(getState()));
                break;
        }
        notifyTaskChanged();

        // else do nothing

        Log.d(TAG, "reach the end of file download task with flag success is "+ success);
        Log.d(PartialDownloadTask.TAG, "reach the end of file download task with flag success is "+success);
    }

    private void releaseConnection(HttpURLConnection urlConnection, InputStream inputStream, DataOutput fileWriter) {
        if(fileWriter instanceof Closeable)
            releaseConnection(urlConnection,inputStream,(Closeable) fileWriter);
    }

    private void releaseConnection(HttpURLConnection urlConnection, InputStream inputStream, Closeable fileWriter) {
        if(urlConnection!=null) try {
            urlConnection.disconnect();
        } catch (Exception ignored) {}

        if(inputStream!=null)
            try {
                inputStream.close();
            } catch (Exception ignored) {}

        if(fileWriter!=null)
            try {
                fileWriter.close();
            } catch (IOException ignored) {}
    }
    public boolean isTaskFailed() {
        return getState()==FAILURE_TERMINATED;
    }

    public synchronized void notifyPartialTaskChanged(PartialDownloadTask task) {

        switch (task.getState()) {
            case PENDING:
                // do nothing
                break;
            case RUNNING:
                // just notify progress
                // do nothing
                break;
            case SUCCESS:
                // one partial task is success
                // we still do nothing
                break;
            case FAILURE_TERMINATED:
                // one failed
                switch (getState()) {
                    case RUNNING:
                      // one failed means task failed
                      setState(FAILURE_TERMINATED, task.getMessage());
                      Log.d(TAG,"partial task "+task.getId()+" is failure terminated with message: "+task.getMessage());
                        break;
                    case FAILURE_TERMINATED:
                    default:
                        // do nothing
                }
                break;
        }
 /*       StringBuilder progress = new StringBuilder();
        progress.append("log progress: ");
        for (int i = 0; i < mPartialDownloadTasks.size(); i++) {
            progress.append(" task_").append(i + 1).append(" = ").append((int) (100* mPartialDownloadTasks.get(i).getDownloadedInBytes() / mPartialDownloadTasks.get(i).getDownloadLength()));
        }
        Log.d(TAG, progress.toString());*/
        notifyTaskChanged();
    }

    public int getConnectionNumber() {
        if(getTaskManager()!=null)
        return getTaskManager().getConnectionsPerTask();
        return BaseTaskController.getRecommendConnectionPerTask();
    }
}
