package com.ospreydcs.dp.gui.component;

import com.ospreydcs.dp.gui.testutil.FxToolkitSupport;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the shared hyperlink cell extracted from the four explore views.
 *
 * The cells are exercised through a real {@link TableView} attached to a {@link Scene} rather than
 * by calling {@code updateItem} directly.  Two reasons: {@code updateItem} is protected, and more
 * importantly the cell reads its values from {@code getTableRow().getItem()}, which is populated
 * only by the table's own layout pass.  A test that stubbed the row would prove nothing about the
 * code path the application actually runs.
 */
public class HyperlinkListTableCellTest {

    @BeforeAll
    public static void startToolkit() throws Exception {
        FxToolkitSupport.ensureStarted();
    }

    /** Minimal row type: a display string plus the list the cell should actually link. */
    public static class Row {
        private final SimpleStringProperty display;
        private final List<String> values;

        Row(String display, List<String> values) {
            this.display = new SimpleStringProperty(display);
            this.values = values;
        }

        public SimpleStringProperty displayProperty() {
            return display;
        }

        public List<String> getValues() {
            return values;
        }
    }

    /**
     * Builds a one-column table, forces it to lay out, and returns the rendered cells.
     *
     * The table is put in a Scene and given a real size because a TableView that never lays out
     * never creates cells at all — the test would then pass vacuously against zero cells.
     */
    private static List<TableCell<Row, String>> renderCells(
            List<Row> rows,
            Callback<TableColumn<Row, String>, TableCell<Row, String>> cellFactory
    ) throws Exception {
        return FxToolkitSupport.callOnFxThread(() -> {
            final TableView<Row> table = new TableView<>(FXCollections.observableArrayList(rows));
            final TableColumn<Row, String> column = new TableColumn<>("values");
            column.setCellValueFactory(data -> data.getValue().displayProperty());
            column.setCellFactory(cellFactory);
            column.setPrefWidth(400);
            table.getColumns().add(column);

            final Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(table), 600, 400));
            // Lay out without showing a window: applyCss + layout drives cell creation, and the
            // suite stays windowless (see FxToolkitSupport).
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            table.applyCss();
            table.layout();

            final List<TableCell<Row, String>> cells = new ArrayList<>();
            collectCells(table, cells);
            return cells;
        });
    }

    @SuppressWarnings("unchecked")
    private static void collectCells(javafx.scene.Node node, List<TableCell<Row, String>> into) {
        if (node instanceof TableCell<?, ?> cell) {
            into.add((TableCell<Row, String>) cell);
            return;
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                collectCells(child, into);
            }
        }
    }

    /** The cells backing actual rows, in row order — recycled empty cells are dropped. */
    private static List<TableCell<Row, String>> populatedCells(
            List<TableCell<Row, String>> cells, int expectedCount
    ) {
        final List<TableCell<Row, String>> populated = new ArrayList<>();
        for (TableCell<Row, String> cell : cells) {
            if (cell.getTableRow() != null && cell.getTableRow().getItem() != null) {
                populated.add(cell);
            }
        }
        assertEquals(expectedCount, populated.size(),
                "expected exactly " + expectedCount + " cells backed by rows");
        return populated;
    }

    private static List<Hyperlink> linksIn(TableCell<Row, String> cell) {
        final List<Hyperlink> links = new ArrayList<>();
        if (cell.getGraphic() instanceof Hyperlink single) {
            links.add(single);
        } else if (cell.getGraphic() instanceof HBox box) {
            for (javafx.scene.Node child : box.getChildren()) {
                if (child instanceof Hyperlink link) {
                    links.add(link);
                }
            }
        }
        return links;
    }

    @Test
    public void eachValueBecomesItsOwnLink() throws Exception {
        final List<TableCell<Row, String>> cells = renderCells(
                List.of(new Row("a, b, c", List.of("a", "b", "c"))),
                HyperlinkListTableCell.forValues(Row::getValues, (row, value) -> { }));

        final TableCell<Row, String> cell = populatedCells(cells, 1).get(0);
        final List<Hyperlink> links = linksIn(cell);

        assertEquals(3, links.size(), "one link per value");
        assertEquals("a", links.get(0).getText());
        assertEquals("b", links.get(1).getText());
        assertEquals("c", links.get(2).getText());
        assertTrue(links.get(0).getStyleClass().contains(HyperlinkListTableCell.HYPERLINK_STYLE_CLASS));
        assertNull(cell.getText(), "text must be cleared so it does not render beside the graphic");
    }

    @Test
    public void separatorsAppearBetweenValuesOnly() throws Exception {
        final List<TableCell<Row, String>> cells = renderCells(
                List.of(new Row("a, b", List.of("a", "b"))),
                HyperlinkListTableCell.forValues(Row::getValues, (row, value) -> { }));

        final HBox box = assertInstanceOf(HBox.class, populatedCells(cells, 1).get(0).getGraphic());
        final List<Label> separators = new ArrayList<>();
        for (javafx.scene.Node child : box.getChildren()) {
            if (child instanceof Label label && !(child instanceof Hyperlink)) {
                separators.add(label);
            }
        }

        assertEquals(1, separators.size(), "two values take exactly one separator");
        assertEquals(", ", separators.get(0).getText());
    }

    @Test
    public void clickingALinkPassesBothTheRowAndTheClickedValue() throws Exception {
        final Row row = new Row("x, y", List.of("x", "y"));
        final List<String> clickedValues = new ArrayList<>();
        final List<Row> clickedRows = new ArrayList<>();

        final List<TableCell<Row, String>> cells = renderCells(
                List.of(row),
                HyperlinkListTableCell.forValues(Row::getValues, (clickedRow, value) -> {
                    clickedRows.add(clickedRow);
                    clickedValues.add(value);
                }));

        final List<Hyperlink> links = linksIn(populatedCells(cells, 1).get(0));
        FxToolkitSupport.runOnFxThread(() -> links.get(1).fire());

        assertEquals(List.of("y"), clickedValues, "the clicked value, not the first one");
        assertEquals(List.of(row), clickedRows, "the handler receives the row it was rendered for");
    }

    /**
     * The provider-name case: the link is labelled with one field and navigates by another, which
     * is why onClick takes the row rather than only the displayed value.
     */
    @Test
    public void aSingleValueCellLinksTheDisplayedValueAndStillHandsBackTheRow() throws Exception {
        final Row row = new Row("provider-name", List.of("ignored-by-single-value"));
        final List<Row> clickedRows = new ArrayList<>();

        final List<TableCell<Row, String>> cells = renderCells(
                List.of(row),
                HyperlinkListTableCell.forSingleValue(
                        r -> r.displayProperty().get(),
                        (clickedRow, value) -> clickedRows.add(clickedRow)));

        final List<Hyperlink> links = linksIn(populatedCells(cells, 1).get(0));
        assertEquals(1, links.size());
        assertEquals("provider-name", links.get(0).getText());

        FxToolkitSupport.runOnFxThread(() -> links.get(0).fire());
        assertEquals(List.of(row), clickedRows);
    }

    @Test
    public void aRowWithNoValuesRendersNothing() throws Exception {
        final List<TableCell<Row, String>> cells = renderCells(
                List.of(new Row("", List.of())),
                HyperlinkListTableCell.forValues(Row::getValues, (row, value) -> { }));

        final TableCell<Row, String> cell = populatedCells(cells, 1).get(0);
        assertNull(cell.getGraphic(), "no values means no graphic");
        assertNull(cell.getText());
    }

    @Test
    public void aBlankSingleValueRendersNothingRatherThanAnInvisibleLink() throws Exception {
        final List<TableCell<Row, String>> cells = renderCells(
                List.of(new Row("   ", List.of())),
                HyperlinkListTableCell.forSingleValue(
                        r -> r.displayProperty().get(), (row, value) -> { }));

        assertNull(populatedCells(cells, 1).get(0).getGraphic(),
                "a blank label would be an invisible click target");
    }

    /**
     * Guards the first of the two defects the extraction fixed.
     *
     * The hand-written cells decided emptiness from the cell's display string and returned early
     * when it was blank.  That coupled rendering to a formatting decision made in the row class:
     * a row whose display string was empty but whose value list was not rendered no links at all.
     * Emptiness is now decided by the list itself, so the two cannot disagree.
     */
    @Test
    public void valuesRenderEvenWhenTheDisplayStringIsBlank() throws Exception {
        final List<TableCell<Row, String>> cells = renderCells(
                List.of(new Row("", List.of("real-value"))),
                HyperlinkListTableCell.forValues(Row::getValues, (row, value) -> { }));

        final List<Hyperlink> links = linksIn(populatedCells(cells, 1).get(0));
        assertEquals(1, links.size(), "a blank display string must not suppress real values");
        assertEquals("real-value", links.get(0).getText());
    }

    /**
     * Guards the second defect: a value containing a comma.
     *
     * Recovering the values by splitting the display string on ", " would yield three links here
     * instead of two, each mislabelled and each navigating to an id that does not exist.  Reading
     * the row's list makes the display string's formatting irrelevant.
     */
    @Test
    public void aValueContainingACommaStaysOneLink() throws Exception {
        final List<String> values = List.of("name, with comma", "plain");
        final List<String> clicked = new ArrayList<>();

        final List<TableCell<Row, String>> cells = renderCells(
                List.of(new Row(String.join(", ", values), values)),
                HyperlinkListTableCell.forValues(Row::getValues, (row, value) -> clicked.add(value)));

        final List<Hyperlink> links = linksIn(populatedCells(cells, 1).get(0));
        assertEquals(2, links.size(), "the comma inside a value must not split it");
        assertEquals("name, with comma", links.get(0).getText());

        FxToolkitSupport.runOnFxThread(() -> links.get(0).fire());
        assertEquals(List.of("name, with comma"), clicked,
                "the handler must receive the whole value, not a fragment of it");
    }

    /**
     * Cells are recycled as the user scrolls, so a cell that once rendered links must not keep
     * them when it is reused for a row that has none.
     */
    /**
     * Cells are recycled as the user scrolls, so a cell that once rendered links must not keep
     * them when it is reused for a row that has none.
     *
     * The reuse is driven directly rather than by swapping the table's items: replacing the items
     * makes the table rebuild its row-to-cell association over several pulses, so inspecting it
     * immediately after a layout finds cells not yet bound to rows.  Calling updateIndex is what
     * the virtualized table itself does to repoint a cell at a different row, so this exercises
     * the same path with none of the timing.
     */
    @Test
    public void recyclingACellForAnEmptyRowClearsTheStaleLinks() throws Exception {
        FxToolkitSupport.runOnFxThread(() -> {
            final TableView<Row> table = new TableView<>(FXCollections.observableArrayList(
                    new Row("a, b", List.of("a", "b")),
                    new Row("", List.of())));
            final TableColumn<Row, String> column = new TableColumn<>("values");
            column.setCellValueFactory(data -> data.getValue().displayProperty());
            column.setCellFactory(HyperlinkListTableCell.forValues(Row::getValues, (r, v) -> { }));
            column.setPrefWidth(400);
            table.getColumns().add(column);

            final Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(table), 600, 400));
            // The Scene root must lay out before the table creates any cells; see renderCells.
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            table.applyCss();
            table.layout();

            final List<TableCell<Row, String>> cells = new ArrayList<>();
            collectCells(table, cells);
            final TableCell<Row, String> cell = populatedCells(cells, 2).get(0);
            assertNotNull(cell.getGraphic(),
                    "precondition: the cell rendered links for the populated row");

            // Repoint the same cell at the empty row, exactly as scrolling would.
            cell.updateIndex(1);

            assertNull(cell.getGraphic(),
                    "a recycled cell must not keep the previous row's links");
            assertNull(cell.getText());
        });
    }

    @Test
    public void aMissingHandlerIsRejectedAtConstructionRatherThanOnClick() {
        assertThrows(IllegalArgumentException.class,
                () -> new HyperlinkListTableCell<Row>(Row::getValues, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new HyperlinkListTableCell<Row>(null, null, (row, value) -> { }));
    }
}
