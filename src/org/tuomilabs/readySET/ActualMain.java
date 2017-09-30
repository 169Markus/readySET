package org.tuomilabs.readySET;

import boofcv.alg.distort.RemovePerspectiveDistortion;
import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.alg.filter.binary.ThresholdImageOps;
import boofcv.alg.misc.ImageStatistics;
import boofcv.alg.shapes.ShapeFittingOps;
import boofcv.gui.ListDisplayPanel;
import boofcv.gui.feature.VisualizeShapes;
import boofcv.gui.image.ShowImages;
import boofcv.io.UtilIO;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.ConnectRule;
import boofcv.struct.PointIndex_I32;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.Planar;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I32;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class ActualMain {
    static int CARD_MIN_DIAGONAL_SIZE_PIXELS = 500;
    static int FILENAME_START_INTEGER = 999;

    // Polynomial fitting tolerances
    static double splitFraction = 0.05;
    static double minimumSideFraction = 0.01;

    static ListDisplayPanel gui = new ListDisplayPanel();

    /**
     * Fits polygons to found contours around binary blobs.
     */
    private static void fitBinaryImage(GrayF32 input, String path) throws IOException {

        GrayU8 binary = new GrayU8(input.width, input.height);
        BufferedImage polygon = new BufferedImage(input.width, input.height, BufferedImage.TYPE_INT_RGB);

        // the mean pixel value is often a reasonable threshold when creating a binary image
        double mean = ImageStatistics.mean(input);

        // create a binary image by thresholding
        ThresholdImageOps.threshold(input, binary, (float) mean, true);

        // reduce noise with some filtering
        GrayU8 filtered = BinaryImageOps.erode8(binary, 1, null);
        filtered = BinaryImageOps.dilate8(filtered, 1, null);

        // Find the contour around the shapes
        List<Contour> contours = BinaryImageOps.contour(filtered, ConnectRule.EIGHT, null);

        // Fit a polygon to each shape and draw the results
        Graphics2D g2 = polygon.createGraphics();
        g2.setStroke(new BasicStroke(2));

        int count = FILENAME_START_INTEGER;
        for (Contour c : contours) {
            // Fit the polygon to the found external contour.  Note loop = true
            List<PointIndex_I32> vertexes = ShapeFittingOps.fitPolygon(c.external, true,
                    splitFraction, minimumSideFraction, 100);

            g2.setColor(Color.RED);
            int longDiagonal = getLongDiagonal(vertexes);
            //System.out.println(longDiagonal);

            if (longDiagonal > CARD_MIN_DIAGONAL_SIZE_PIXELS) {
                VisualizeShapes.drawPolygon(vertexes, true, g2);
                extractPolygon(vertexes, count++, path);
            }

            // handle internal contours now
            g2.setColor(Color.BLUE);
            for (List<Point2D_I32> internal : c.internal) {
                vertexes = ShapeFittingOps.fitPolygon(internal, true, splitFraction, minimumSideFraction, 100);
                VisualizeShapes.drawPolygon(vertexes, true, g2);
            }
        }

        gui.addImage(polygon, "Binary Blob Contours");
    }

    private static void extractPolygon(List<PointIndex_I32> external, int i, String path) throws IOException {
        int[] imageBounds = getBounds(external);

        BufferedImage in = ImageIO.read(new File(path));
        Polygon inputPolygon = convertToPolygon(external);
        Rectangle bounds = inputPolygon.getBounds(); // Polygon inputPolygon
        BufferedImage extractor = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = extractor.createGraphics();
        inputPolygon.translate(-bounds.x, -bounds.y);
        g.setClip(inputPolygon);
        g.drawImage(in, -bounds.x, -bounds.y, null);


//        AffineTransform transform = new AffineTransform();
//        transform.rotate(Math.PI / 5, extractor.getWidth()/2, extractor.getHeight()/2);
//        AffineTransformOp op = new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR);
//        extractor = op.filter(extractor, null);


//        File extImageFile = new File("out_" + i + ".png");
//        ImageIO.write(extractor, "png", extImageFile);

//        BufferedImage buffered = UtilImageIO.loadImage(UtilIO.pathExample("out_" + i + ".png"));

        BufferedImage copy = new BufferedImage(extractor.getWidth(), extractor.getHeight(), BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = copy.createGraphics();
        g2d.setColor(Color.WHITE); // Or what ever fill color you want...
        g2d.fillRect(0, 0, copy.getWidth(), copy.getHeight());
        g2d.drawImage(extractor, 0, 0, null);
        g2d.dispose();


//        File extImageFile111 = new File("outtest_" + i + ".png");
//        ImageIO.write(copy, "png", extImageFile111);


        Planar<GrayF32> input = ConvertBufferedImage.convertFromPlanar(copy, null, true, GrayF32.class);

        RemovePerspectiveDistortion<Planar<GrayF32>> removePerspective =
                new RemovePerspectiveDistortion<>(825, 550, ImageType.pl(3, GrayF32.class));

        System.out.println(external);

        assert external.size() == 4;

        int minX = imageBounds[0];
        int minY = imageBounds[1];

        Point2D_F64 coordt0 = new Point2D_F64(external.get(0).x - minX, external.get(0).y - minY);
        Point2D_F64 coordt1 = new Point2D_F64(external.get(1).x - minX, external.get(1).y - minY);
        Point2D_F64 coordt2 = new Point2D_F64(external.get(2).x - minX, external.get(2).y - minY);
        Point2D_F64 coordt3 = new Point2D_F64(external.get(3).x - minX, external.get(3).y - minY);

        int sum0 = external.get(0).x + external.get(0).y;
        int sum3 = external.get(3).x + external.get(3).y;

        Point2D_F64 coord0;
        Point2D_F64 coord1;
        Point2D_F64 coord2;
        Point2D_F64 coord3;

        if (sum0 < sum3) {
            coord0 = coordt1;
            coord1 = coordt2;
            coord2 = coordt3;
            coord3 = coordt0;
        } else {
            coord0 = coordt2;
            coord1 = coordt3;
            coord2 = coordt0;
            coord3 = coordt1;
        }


        // Specify the corners in the input image of the region.
        // Order matters! top-left, top-right, bottom-right, bottom-left
        if (!removePerspective.apply(input,
                coord0, coord1, coord2, coord3)) {
            throw new RuntimeException("Failed!?!?");
        }
//            if (!removePerspective.apply(input,
//                    new Point2D_F64(10, 10), new Point2D_F64(700, 30),
//                    new Point2D_F64(700, 700), new Point2D_F64(10, 700))) {
//                throw new RuntimeException("Failed!?!?");
//            }
        Planar<GrayF32> output = removePerspective.getOutput();

        BufferedImage flat = ConvertBufferedImage.convertTo_F32(output, null, true);

        File extImageFile = new File("C:\\development\\readySET\\deck\\out_" + i + ".png");
        ImageIO.write(flat, "png", extImageFile);
    }

    private static Polygon convertToPolygon(List<PointIndex_I32> external) {
        Polygon a = new Polygon();

        for (Point2D_I32 point : external) {
            a.addPoint(point.x, point.y);
        }

        return a;
    }

    private static int getLongDiagonal(List<PointIndex_I32> vertexes) {
        int[] bounds = getBounds(vertexes);

        double x1 = (double) bounds[0];
        double y1 = (double) bounds[1];
        double x2 = (double) bounds[2];
        double y2 = (double) bounds[3];

        return (int) Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    private static int[] getBounds(List<PointIndex_I32> vertices) {
        int minX = 1000000;
        int minY = 1000000; // Fuck people with higher resolution
        int maxX = 0;
        int maxY = 0;

        for (Point2D_I32 vertex : vertices) {
            int currentX = vertex.x;
            int currentY = vertex.y;

            if (currentX > maxX) {
                maxX = currentX;
            }
            if (currentX < minX) {
                minX = currentX;
            }
            if (currentY > maxY) {
                maxY = currentY;
            }
            if (currentY < minY) {
                minY = currentY;
            }
        }

        return new int[]{minX, minY, maxX, maxY};
    }

    public static void runExtraction(String path) throws IOException {
        // load and convert the image into a usable format
        BufferedImage image = UtilImageIO.loadImage(UtilIO.pathExample("C:\\development\\readySET\\saved.png"));
        GrayF32 input = ConvertBufferedImage.convertFromSingle(image, null, GrayF32.class);

        gui.addImage(image, "Original");

//        fitCannyEdges(input);
//        fitCannyBinary(input);
        fitBinaryImage(input, path);

        ShowImages.showWindow(gui, "Polygon from Contour", true);
    }
}