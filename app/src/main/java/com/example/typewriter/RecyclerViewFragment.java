package com.example.typewriter;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.example.typewrite.R;
import com.github.ilyagh.TypewriterRefreshLayout;

public class RecyclerViewFragment extends Fragment {
    private static final int REFRESH_DELAY_MS = 8000;
    private static final int ITEMS_COUNT = 25;

    @BindView(R.id.pullToRefresh)
    TypewriterRefreshLayout pullToRefresh;
    @BindView(R.id.recyclerView)
    RecyclerView recyclerView;

    private boolean isRefreshing;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_recycler_view, container, false);
        ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initRecyclerView();
        initRefreshView();

        pullToRefresh.post(new Runnable() {
            @Override
            public void run() {
                pullToRefresh.setRefreshing(isRefreshing);
            }
        });
    }

    private void initRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setAdapter(new SampleAdapter());
    }

    private void initRefreshView() {
        pullToRefresh.setOnRefreshListener(new TypewriterRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                isRefreshing = true;
                pullToRefresh.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        pullToRefresh.setRefreshing(isRefreshing = false);
                    }
                }, REFRESH_DELAY_MS);
            }
        });
    }

    class SampleAdapter extends RecyclerView.Adapter<RecyclerViewFragment.SampleHolder> {
        @Override
        public RecyclerViewFragment.SampleHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item, parent, false);
            return new RecyclerViewFragment.SampleHolder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerViewFragment.SampleHolder holder, int position) {
        }

        @Override
        public int getItemCount() {
            return ITEMS_COUNT;
        }
    }

    static class SampleHolder extends RecyclerView.ViewHolder {
        SampleHolder(View itemView) {
            super(itemView);
        }
    }
}

