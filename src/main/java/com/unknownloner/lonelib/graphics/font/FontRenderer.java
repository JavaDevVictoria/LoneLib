package com.unknownloner.lonelib.graphics.font;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;

import com.unknownloner.lonelib.graphics.buffers.VertexArrayObject;
import com.unknownloner.lonelib.graphics.buffers.VertexAttribPointer;
import com.unknownloner.lonelib.graphics.buffers.VertexBufferObject;
import com.unknownloner.lonelib.graphics.shaders.ShaderProgram;
import com.unknownloner.lonelib.graphics.textures.Texture;
import com.unknownloner.lonelib.math.Vec3;
import com.unknownloner.lonelib.math.Vec4;
import com.unknownloner.lonelib.util.BufferUtil;
import com.unknownloner.lonelib.util.ImageUtil;
import com.unknownloner.lonelib.util.MathUtil;

public class FontRenderer {
	
	
	public static ShaderProgram globalFontProgram = new ShaderProgram("/shaders/FontShader.vert", "/shaders/FontShader.frag");
	private Texture fontTexture;
	private ShaderProgram shaderProgram = globalFontProgram;
	private boolean[] displayable = new boolean[256];
	private float[] widths = new float[256];
	private float[] texWidths = new float[256];
	private float texHeight;
	private FloatBuffer dataBuffer = BufferUtil.createFloatBuffer(128 * 9 * 4); //128 chars * 9 elements * 4 vertices per element
	private VertexBufferObject data = new VertexBufferObject(GL15.GL_ARRAY_BUFFER);
	private VertexBufferObject inds = new VertexBufferObject(GL15.GL_ELEMENT_ARRAY_BUFFER);
	private VertexArrayObject vao = new VertexArrayObject(
			new VertexAttribPointer(data, 0, 3, GL11.GL_FLOAT, false, 9 * 4, 0),     //Position
			new VertexAttribPointer(data, 1, 4, GL11.GL_FLOAT, false, 9 * 4, 3 * 4), //Color
			new VertexAttribPointer(data, 2, 2, GL11.GL_FLOAT, false, 9 * 4, 7 * 4)  //Tex Coords
	);
	
	public FontRenderer(Font font) {
		int size = ImageUtil.ascent(font) + ImageUtil.descent(font);
		int cellSize = MathUtil.nextPowerOfTwo(size);
		BufferedImage img = new BufferedImage(cellSize * 16, cellSize * 16, BufferedImage.TYPE_INT_ARGB);
		texHeight = size / (float)img.getHeight();
		Graphics2D g = img.createGraphics();
		g.setColor(new Color(255, 255, 255, 0));
		g.fillRect(0, 0, img.getWidth(), img.getHeight());
		g.setColor(new Color(255, 255, 255, 255));
		g.setFont(font);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		int ascent = g.getFontMetrics().getAscent();
		for(int i = 0; i < 256; i++) {
			if(font.canDisplay(i)) {
				int x = (i % 16) * cellSize;
				int y = (i / 16) * cellSize + ascent; //Text is drawn from the baseline
				displayable[i] = true;
				String charString = Character.toString((char)i);
				int width = g.getFontMetrics().stringWidth(charString);
				widths[i] = width / (float)size;
				texWidths[i] = width / (float)img.getWidth();
				g.drawString(charString, x, y);
			}
		}
		g.dispose();
		fontTexture = new Texture(img, true, true, false);
		
		inds.assign();
		ShortBuffer indBuf = BufferUtil.createShortBuffer(128 * 6);
		for(short i = 0; i < 128 * 4; i += 4) {
			short[] ind = new short[] {
					i,
					(short)(i + 1),
					(short)(i + 2),
					(short)(i + 2),
					(short)(i + 3),
					i
			};
			indBuf.put(ind);
		}
		indBuf.flip();
		inds.bufferData(indBuf, GL15.GL_STATIC_DRAW);
	}
	
	public void setShaderProgram(ShaderProgram prgm, int posAttrib, int colorAttrib, int texCoordAttrib) {
		this.shaderProgram = prgm;
		vao.delete();
		vao = new VertexArrayObject(
				new VertexAttribPointer(data, posAttrib,      3, GL11.GL_FLOAT, false, 9 * 4, 0),     //Position
				new VertexAttribPointer(data, colorAttrib,    4, GL11.GL_FLOAT, false, 9 * 4, 3 * 4), //Color
				new VertexAttribPointer(data, texCoordAttrib, 2, GL11.GL_FLOAT, false, 9 * 4, 7 * 4)  //Tex Coords
		);
	}
	
	public ShaderProgram getShaderProgram() {
		return this.shaderProgram;
	}
	
	public void drawString(String text, Vec3 startPos, Vec4 color, float charSize) {
		fontTexture.assign(GL13.GL_TEXTURE0);
		shaderProgram.assign();
		vao.assign();
		inds.assign();
		int strPos = 0;
		char[] textChars = text.toCharArray();
		do {
			float x = startPos.getX();
			float y = startPos.getY();
			float z = startPos.getZ();
			//TODO add support for color changes mid string, possibly with control characters
			float r = color.getX();
			float g = color.getY();
			float b = color.getZ();
			float a = color.getW();
			dataBuffer.clear();
			for(int i = strPos, max = (textChars.length - strPos < 128 ? textChars.length : strPos + 128); i < max; i++) {
				char c = textChars[i];
				if(displayable[c]) {
					float tX = (c % 16) / 16F;
					float tY = (c / 16) / 16F;
					float width = widths[c] * charSize;
					dataBuffer.put(new float[] {
							x,            y + charSize, z, r, g, b, a, tX,                tY,
							x,            y,            z, r, g, b, a, tX,                tY + texHeight,
							x + width,    y,            z, r, g, b, a, tX + texWidths[c], tY + texHeight,
							x + width,    y + charSize, z, r, g, b, a, tX + texWidths[c], tY,
					});
					x += width;
				}
			}
			dataBuffer.flip();
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, dataBuffer, GL15.GL_STREAM_DRAW);
			GL11.glDrawElements(GL11.GL_TRIANGLES, textChars.length * 6, GL11.GL_UNSIGNED_SHORT, 0);
			strPos += 128;
		} while(strPos < textChars.length);
	}
	
	public void drawCenteredString(String text, Vec3 startPos, Vec4 color, float charSize) {
		drawString(text, startPos.sub(new Vec3(stringWidth(text, charSize) / 2F, 0, 0)), color, charSize);
	}
	
	public float stringWidth(String text, float charSize) {
		char[] textChars = text.toCharArray();
		float width = 0;
		for(char c : textChars) {
			width += widths[c] * charSize;
		}
		return width;
	}

}
