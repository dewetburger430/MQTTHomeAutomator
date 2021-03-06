package util;

import java.util.logging.Logger;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

public class Firebase {
    private static final Logger LOG = Logger.getLogger(Firebase.class.getName());

    public static DataSnapshot readObject(DatabaseReference ref) {
        final Object sync = new Object();

        DataSnapshot[] retval = new DataSnapshot[1];

        ref.addListenerForSingleValueEvent(new ValueEventListener() {

            @Override
            public void onDataChange(DataSnapshot snapshot) {
                try {
                    LOG.fine("Data retrieved for " + ref.getPath() + ": " + snapshot);
                    if (!snapshot.getValue().equals("null")) {
                        retval[0] = snapshot;
                    }
                } catch (Exception e) {
                } finally {
                    synchronized (sync) {
                        sync.notifyAll();
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                LOG.warning("Could not read data: " + error.getMessage());
                synchronized (sync) {
                    sync.notifyAll();
                }
            }
        });
        synchronized (sync) {
            try {
                sync.wait();
            } catch (Exception e) {
            }
        }

        return retval[0];
    }
}