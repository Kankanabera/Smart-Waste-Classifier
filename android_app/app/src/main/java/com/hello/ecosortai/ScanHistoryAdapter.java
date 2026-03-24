package com.hello.ecosortai;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

/**
 * ScanHistoryAdapter
 * ------------------
 * Binds a List<ScanRecord> to the "Recent Scans" RecyclerView.
 * Each row shows: "Plastic - 91% confidence - 10:45 AM"
 */
public class ScanHistoryAdapter extends RecyclerView.Adapter<ScanHistoryAdapter.ViewHolder> {

    private final List<ScanRecord> records;

    public ScanHistoryAdapter(List<ScanRecord> records) {
        this.records = records;
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvEntry;

        ViewHolder(View itemView) {
            super(itemView);
            tvEntry = itemView.findViewById(R.id.tvHistoryEntry);
        }
    }

    // ── Adapter callbacks ─────────────────────────────────────────────────────

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_scan_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ScanRecord record = records.get(position);
        // Format: "Plastic - 91% confidence - 10:45 AM"
        String text = String.format(Locale.US,
                "%s - %.0f%% confidence - %s",
                record.label,
                record.confidence * 100f,
                record.timestamp);
        holder.tvEntry.setText(text);
    }

    @Override
    public int getItemCount() {
        return records.size();
    }
}
