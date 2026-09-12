package com.ospreydcs.dp.gui.component;

import javafx.geometry.Insets;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.util.Callback;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A {@link TableCell} rendering a row's multi-valued field as a comma-separated run of
 * {@link Hyperlink}s, one per value.
 *
 * <p>This replaces three near-identical hand-written cells (provider PV names, an annotation's
 * related datasets, an annotation's related annotations) and the single-value provider-name cell.
 * Each differed only in where the values came from and what a click did, so both are constructor
 * parameters here:
 *
 * <ul>
 *   <li>{@code valuesExtractor} — the values to link, taken from the <em>row</em> rather than from
 *       the cell's item;</li>
 *   <li>{@code labelMapper} — the text for one value, defaulting to the value itself;</li>
 *   <li>{@code onClick} — given the row and the clicked value, since some navigations need a
 *       different field of the row than the one displayed (provider-name links navigate by
 *       provider <em>id</em>).</li>
 * </ul>
 *
 * <p><strong>The values come from the row, not from the cell item, and that is deliberate.</strong>
 * The item is the column's display string — typically the values already joined with ", " by the
 * row class. Re-splitting that string to recover the values would break on any value containing a
 * comma, and would silently produce wrong links rather than failing. The row's list accessor is the
 * single source of truth; the item string is used only to decide whether the cell is empty.
 *
 * <p><strong>Emptiness is decided by the extracted values, not by the item string.</strong> The
 * hand-written cells tested {@code item.trim().isEmpty()} and returned early, which coupled
 * rendering to a formatting decision made elsewhere: a row whose display string was blank but whose
 * list was non-empty rendered no links at all. Testing the list directly makes the two impossible to
 * disagree. A cell whose extracted list is empty renders nothing, exactly as before.
 *
 * <p>Cells are recycled by {@code TableView} as the user scrolls, so {@code updateItem} must fully
 * repaint — every path either populates the container or clears it, and {@code setText(null)} is
 * always paired with {@code setGraphic}, since leaving the default text set would double-render
 * beside the graphic.
 */
public class HyperlinkListTableCell<S> extends TableCell<S, String> {

    /** CSS class applied to every link, matching the hand-written cells this replaces. */
    public static final String HYPERLINK_STYLE_CLASS = "hyperlink-small";

    /** CSS class applied to the ", " separators. */
    public static final String SEPARATOR_STYLE_CLASS = "text-muted";

    private static final String SEPARATOR_TEXT = ", ";

    private final HBox content;
    private final Function<S, List<String>> valuesExtractor;
    private final Function<String, String> labelMapper;
    private final BiConsumer<S, String> onClick;

    public HyperlinkListTableCell(
            Function<S, List<String>> valuesExtractor,
            Function<String, String> labelMapper,
            BiConsumer<S, String> onClick
    ) {
        if (valuesExtractor == null) {
            throw new IllegalArgumentException("valuesExtractor is required");
        }
        if (onClick == null) {
            throw new IllegalArgumentException("onClick is required");
        }

        this.valuesExtractor = valuesExtractor;
        this.labelMapper = labelMapper != null ? labelMapper : Function.identity();
        this.onClick = onClick;

        this.content = new HBox();
        this.content.setSpacing(5);
        this.content.setPadding(new Insets(2, 5, 2, 5));
    }

    /**
     * Cell factory for a column whose row type exposes a list of values.
     *
     * <p>The returned callback builds a new cell per column, which is what {@code TableView}
     * expects; the cell itself is reused across rows by the virtualized table.
     */
    public static <S> Callback<TableColumn<S, String>, TableCell<S, String>> forValues(
            Function<S, List<String>> valuesExtractor,
            BiConsumer<S, String> onClick
    ) {
        return column -> new HyperlinkListTableCell<>(valuesExtractor, null, onClick);
    }

    /**
     * Cell factory for a column rendering exactly one link per row, labelled with the cell's own
     * display value.
     *
     * <p>The single-value case is the same widget with a one-element list, so it shares this cell
     * rather than a second class. {@code onClick} still receives the row, which is what lets a
     * provider-name link navigate by the row's provider id.
     *
     * <p>A row whose label is null or blank renders nothing — there is no link worth clicking, and
     * an empty hyperlink is an invisible click target.
     */
    public static <S> Callback<TableColumn<S, String>, TableCell<S, String>> forSingleValue(
            Function<S, String> valueExtractor,
            BiConsumer<S, String> onClick
    ) {
        return column -> new HyperlinkListTableCell<>(
                row -> {
                    final String value = valueExtractor.apply(row);
                    return (value == null || value.isBlank()) ? List.of() : List.of(value);
                },
                null,
                onClick);
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);

        setText(null);

        if (empty) {
            setGraphic(null);
            return;
        }

        final S row = resolveRow();
        if (row == null) {
            setGraphic(null);
            return;
        }

        final List<String> values = valuesExtractor.apply(row);
        if (values == null || values.isEmpty()) {
            setGraphic(null);
            return;
        }

        content.getChildren().clear();
        boolean first = true;
        for (String value : values) {
            if (!first) {
                final Label separator = new Label(SEPARATOR_TEXT);
                separator.getStyleClass().add(SEPARATOR_STYLE_CLASS);
                content.getChildren().add(separator);
            }

            final Hyperlink link = new Hyperlink(labelMapper.apply(value));
            link.getStyleClass().add(HYPERLINK_STYLE_CLASS);
            link.setOnAction(event -> onClick.accept(row, value));
            content.getChildren().add(link);

            first = false;
        }

        setGraphic(content);
    }

    /**
     * Resolves the row this cell is currently rendering.
     *
     * <p><strong>The cell's index is authoritative, not its {@code TableRow}.</strong> When a
     * virtualized table recycles a cell it sets the new index and delivers the new item
     * immediately, but repoints the cell's {@code TableRow} in a later pass. A cell that reads
     * {@code getTableRow().getItem()} during that window sees the <em>previous</em> row while
     * holding the new index — so it renders the old row's links against the new row's data, with
     * no error and nothing visibly wrong until a link navigates somewhere unrelated to the row it
     * appears on.
     *
     * <p>Every cell this component replaced read the {@code TableRow} directly and inherited that
     * window. Resolving by index first, and falling back to the {@code TableRow} only when the
     * index is out of range, closes it for all of them at once.
     */
    private S resolveRow() {
        final int index = getIndex();
        final TableView<S> table = getTableView();

        if (table != null && table.getItems() != null
                && index >= 0 && index < table.getItems().size()) {
            return table.getItems().get(index);
        }

        // No usable index (a cell past the end of the data, or one not yet attached to a table):
        // fall back to the row, which is null in exactly those cases too.
        return (getTableRow() != null) ? getTableRow().getItem() : null;
    }
}
