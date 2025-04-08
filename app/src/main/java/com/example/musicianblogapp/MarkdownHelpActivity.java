package com.example.musicianblogapp;

import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;
import androidx.appcompat.widget.Toolbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.musicianblogapp.R;

public class MarkdownHelpActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_markdown_help);
        Toolbar toolbar = findViewById(R.id.toolbarMarkdownHelp);

        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowTitleEnabled(false);
                getSupportActionBar().setTitle(R.string.title_activity_markdown_help);
            }
        }

        TextView helpText = findViewById(R.id.textViewMarkdownHelpContent);

        if (helpText != null) {
            String htmlHelpText = "<h2>Alapvető Formázás:</h2>" +
                    "<p><b>Félkövér:</b><br/><code>**szöveg**</code> vagy <code>__szöveg__</code></p>" +
                    "<p><i>Dőlt:</i><br/><code>*szöveg*</code> vagy <code>_szöveg_</code></p>" +
                    "<p><strike>Áthúzott:</strike> (ha támogatott)<br/><code>~~szöveg~~</code></p>" +
                    "<hr>" + // Vízszintes elválasztó

                    "<h2>Címsorok:</h2>" +
                    "<p><code># Címsor 1</code></p>" +
                    "<p><code>## Címsor 2</code></p>" +
                    "<p><code>### Címsor 3</code> (és így tovább)</p>" +
                    "<hr>" +

                    "<h2>Listák:</h2>" +
                    "<p><b>Számozatlan:</b><br/>" +
                    "<code>- Első elem</code><br/>" +
                    "<code>- Második elem</code><br/>" +
                    "<code>* Vagy csillaggal</code><br/>" +
                    "<code>+ Plusszal is lehet</code></p>" +
                    "<p><b>Számozott:</b><br/>" +
                    "<code>1. Első pont</code><br/>" +
                    "<code>2. Második pont</code></p>" +
                    "<p><b>Teendő lista:</b> (ha támogatott)<br/>" +
                    "<code>- [ ] Feladat (nincs kész)</code><br/>" +
                    "<code>- [x] Feladat (kész)</code></p>" +
                    "<hr>" +

                    "<h2>Egyéb:</h2>" +
                    "<p><b>Linkek:</b><br/><code>[Megjelenő szöveg](http://link.url.cime)</code></p>" +
                    "<p><b>Idézet:</b><br/><code>> Idézett szöveg...</code></p>" + // > jelet escape-elni kell HTML-ben: >
                    "<p><b>Inline Kód:</b><br/>`Ez itt `<code>kiemelt kód</code>` a szövegben.`</p>" +
                    "<p><b>Kódblokk:</b><br/>" +
                    "<code>```<br/>" + // Sortörés HTML-ben: <br/>
                    "Többsoros<br/>" +
                    "kód blokk<br/>" +
                    "```</code></p>" +
                    "<p><b>Vízszintes Vonal:</b><br/><code>---</code> vagy <code>***</code></p>";


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                helpText.setText(Html.fromHtml(htmlHelpText, Html.FROM_HTML_MODE_LEGACY));
            } else {
                helpText.setText(Html.fromHtml(htmlHelpText));
            }
        } else {
            Log.e("MarkdownHelpActivity", "textViewMarkdownHelpContent not found in layout!");
        }
    }

    // onOptionsItemSelected a vissza gombhoz
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}