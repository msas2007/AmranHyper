package com.lap1.mystore.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.lap1.mystore.R;

import java.util.List;
import java.util.Locale;

public class InvoiceItemsAdapter extends BaseAdapter {

    public static class InvoiceItem {
        public String productName;
        public double quantity;
        public double unitPrice;
        public double total;

        public InvoiceItem(String productName, double quantity, double unitPrice, double total) {
            this.productName = productName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.total = total;
        }
    }

    private final List<InvoiceItem> items;
    private final LayoutInflater inflater;

    public InvoiceItemsAdapter(Context context, List<InvoiceItem> items) {
        this.items = items;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View v, ViewGroup parent) {
        if (v == null) {
            v = inflater.inflate(R.layout.item_invoice_detail_row, parent, false);
        }

        InvoiceItem item = items.get(position);

        TextView tvName = v.findViewById(R.id.tvItemName);
        TextView tvQty = v.findViewById(R.id.tvItemQty);
        TextView tvPrice = v.findViewById(R.id.tvItemPrice);
        TextView tvTotal = v.findViewById(R.id.tvItemTotal);

        if (tvName != null) tvName.setText(item.productName);
        if (tvQty != null) {
            if (item.quantity == (long) item.quantity) {
                tvQty.setText(String.format(Locale.getDefault(), "%d", (long) item.quantity));
            } else {
                tvQty.setText(String.format(Locale.getDefault(), "%.2f", item.quantity));
            }
        }
        if (tvPrice != null) tvPrice.setText(String.format(Locale.getDefault(), "%.2f", item.unitPrice));
        if (tvTotal != null) tvTotal.setText(String.format(Locale.getDefault(), "%.2f ر.ي", item.total));

        return v;
    }
}
