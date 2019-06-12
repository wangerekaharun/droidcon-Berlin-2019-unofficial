package com.hellmund.droidcon2019.ui.schedule

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.hellmund.droidcon2019.R
import com.hellmund.droidcon2019.data.model.Talk
import com.hellmund.droidcon2019.data.repository.FavoritesStore
import com.hellmund.droidcon2019.ui.schedule.filter.Filter
import com.hellmund.droidcon2019.util.NotificationScheduler
import kotlinx.android.synthetic.main.list_item_event.view.favoriteButton
import kotlinx.android.synthetic.main.list_item_event.view.presenterTextView
import kotlinx.android.synthetic.main.list_item_event.view.stageTextView
import kotlinx.android.synthetic.main.list_item_event.view.timeContainer
import kotlinx.android.synthetic.main.list_item_event.view.timePeriodTextView
import kotlinx.android.synthetic.main.list_item_event.view.timeTextView
import kotlinx.android.synthetic.main.list_item_event.view.titleTextView
import org.threeten.bp.LocalTime
import org.threeten.bp.format.DateTimeFormatter

sealed class AdapterItem {

    abstract fun bind(
        holder: EventsAdapter.ViewHolder,
        onItemClick: (Talk) -> Unit,
        favoritesStore: FavoritesStore
    )

    data class Header(val time: LocalTime) : AdapterItem() {
        override fun bind(
            holder: EventsAdapter.ViewHolder,
            onItemClick: (Talk) -> Unit,
            favoritesStore: FavoritesStore
        ) = with(holder.itemView) {
            //timeTextView.text = time.format(DateTimeFormatter.ISO_LOCAL_TIME).substring(0, 5)
        }
    }

    data class Event(
        val event: Talk,
        val isFirst: Boolean = false
    ) : AdapterItem() {

        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val timePeriodFormatter = DateTimeFormatter.ofPattern("a")

        override fun bind(
            holder: EventsAdapter.ViewHolder,
            onItemClick: (Talk) -> Unit,
            favoritesStore: FavoritesStore
        ) = with(holder.itemView) {
            timeContainer.visibility = if (isFirst) VISIBLE else INVISIBLE
            timeTextView.text = timeFormatter.format(event.startTime)

            val is24Hours = DateFormat.is24HourFormat(context)
            timePeriodTextView.isVisible = is24Hours.not()
            timePeriodTextView.text = timePeriodFormatter.format(event.startTime)

            titleTextView.text = event.title
            presenterTextView.text = event.speaker
            stageTextView.text = event.stage.name

            updateFavoriteIcon(favoriteButton, favoritesStore)

            favoriteButton.setOnClickListener {
                favoritesStore.toggleFavorite(event)
                updateFavoriteIcon(favoriteButton, favoritesStore)

                if (favoritesStore.isFavorite(event)) {
                    NotificationScheduler(context).schedule(event)
                } else {
                    NotificationScheduler(context).remove(event)
                }
            }

            setOnClickListener { onItemClick(event) }
        }

        private fun updateFavoriteIcon(
            favoriteButton: ImageButton,
            favoritesRepository: FavoritesStore
        ) {
            val isFavorite = favoritesRepository.isFavorite(event)
            val resId = if (isFavorite) R.drawable.ic_baseline_star else R.drawable.outline_star_border
            favoriteButton.setImageResource(resId)
        }

    }

}

class EventsAdapter(
    private val favoritesStore: FavoritesStore,
    private val onItemClick: (Talk) -> Unit
) : RecyclerView.Adapter<EventsAdapter.ViewHolder>() {

    private val rawItems = mutableListOf<Talk>()

    private val allItems = mutableListOf<AdapterItem>()
    private var filteredItems: MutableList<AdapterItem>? = null

    val items: List<AdapterItem>
        get() = filteredItems ?: allItems

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(viewType, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        items[position].bind(holder, onItemClick, favoritesStore)
    }

    override fun getItemCount() = items.size

    fun update(events: List<Talk>) {
        rawItems.clear()
        rawItems += events

        val eventsByTime = events.groupBy { it.startTime }
        val newItems = mutableListOf<AdapterItem>()

        for (time in eventsByTime.keys) {
            val items = eventsByTime[time].orEmpty()
            newItems += items.mapIndexed { index, event ->
                AdapterItem.Event(event, isFirst = index == 0)
            }
        }

        allItems.clear()
        allItems += newItems

        notifyDataSetChanged()
    }

    private fun filterItems(events: List<Talk>, currentFilter: Filter) {
        val filteredEvents = events.filter { it.isInFilter(currentFilter, favoritesStore) }
        val filteredEventsByTime = filteredEvents.groupBy { it.startTime }
        val newFilteredItems = mutableListOf<AdapterItem>()

        for (eventsAtTime in filteredEventsByTime.values) {
            // newFilteredItems += AdapterItem.Header(time)
            newFilteredItems += eventsAtTime.mapIndexed { index, event ->
                AdapterItem.Event(event, isFirst = index == 0)
            }
        }

        filteredItems = newFilteredItems
    }

    fun applyFilter(filter: Filter) {
        if (filter == Filter.EMPTY) {
            filteredItems = null
        } else {
            filterItems(rawItems, filter)
        }

        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is AdapterItem.Header) {
            R.layout.list_item_schedule_header
        } else {
            R.layout.list_item_event
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

}
