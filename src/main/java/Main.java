import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseCredentials;
import com.google.firebase.database.*;
import com.google.firebase.tasks.Task;
import com.google.firebase.tasks.Tasks;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;

import static spark.Spark.*;

public class Main
{
    private static String result = null;
    private static DatabaseReference DBRef;
    private static final String errorTAG = "Error";
    public static void main(String[] args) throws Exception {
        FileInputStream serviceAccount = new FileInputStream("service_key.json");

        Map<String, Object> auth = new HashMap<>();
        auth.put("uid", "Heroku_Token_Generator");

        FirebaseOptions options = new FirebaseOptions.Builder()
                .setCredential(FirebaseCredentials.fromCertificate(serviceAccount))
                .setDatabaseUrl("https://donor3-44858.firebaseio.com")
                .setDatabaseAuthVariableOverride(auth).build();
        FirebaseApp.initializeApp(options);

        DBRef = FirebaseDatabase.getInstance().getReference().child("AuthRequests");

        port(getHerokuAssignedPort());

        get ("/token/:phoneNumber", (request, response) -> {
            String uid = request.params("phoneNumber"); //get the passed parameter
            int code = 100000 + (int)(Math.random()*900000);
            DBRef.child(uid).setValue(code+"");

            new OkHttpClient().newCall(new Request.Builder().url("https://rest.nexmo.com/sms/json?api_key=f7241d7b&api_secret=8afe83820c1fca7f&to=+961"+uid+"&from=Donor3_Project&text=Your cerification code is "+code+".\nThanks for using the app.".replace(" ", "%20").replace("\n", "%0A")).build()).execute();
            return "";
        });

        get("/token/:phoneNumber/:code", (request, response) -> {
            String uid = request.params("phoneNumber");
            String code = request.params("code");

            final Semaphore semaphore = new Semaphore(0);

            DBRef.orderByKey().equalTo(uid).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot)
                {
                    if (dataSnapshot.hasChildren() && code.equals(dataSnapshot.child(uid).getValue(String.class)))
                    {
                        result = generateToken(uid);
                    }
                    else
                    {
                        result = errorTAG;
                    }
                    semaphore.release();
                }

                @Override
                public void onCancelled(DatabaseError databaseError)
                {

                }
            });

            semaphore.acquire();
            DBRef.child(uid).removeValue();
            return result;
        });
    }

    private static int getHerokuAssignedPort()
    {
        ProcessBuilder processBuilder = new ProcessBuilder ();

        if (processBuilder.environment().get("PORT") != null)
        {
            return Integer.parseInt(processBuilder.environment().get("PORT"));
        }

        return 4567 ; //return default port if heroku-port isn't set (i.e. on localhost)
    }

    private static String generateToken(String uid)
    {
        Task<String> authTask = FirebaseAuth.getInstance().createCustomToken(uid);

        try
        {
            Tasks.await(authTask);
        }

        catch (ExecutionException | InterruptedException e)
        {
            return errorTAG;
        }

        return authTask.getResult();
    }
}
