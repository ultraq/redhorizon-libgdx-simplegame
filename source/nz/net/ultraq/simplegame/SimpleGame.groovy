/*
 * Copyright 2025, Emanuel Rabina (http://www.ultraq.net.nz/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nz.net.ultraq.simplegame

import nz.net.ultraq.redhorizon.graphics.Image
import nz.net.ultraq.redhorizon.graphics.Shader
import nz.net.ultraq.redhorizon.graphics.Window
import nz.net.ultraq.redhorizon.graphics.opengl.BasicShader
import nz.net.ultraq.redhorizon.graphics.opengl.OpenGLWindow
import nz.net.ultraq.redhorizon.input.CursorPositionEvent
import nz.net.ultraq.redhorizon.input.InputEvent
import nz.net.ultraq.redhorizon.input.KeyEvent
import nz.net.ultraq.redhorizon.input.MouseButtonEvent

import org.joml.Matrix4f
import org.joml.Vector3f
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.Command
import static org.lwjgl.glfw.GLFW.*

import java.util.concurrent.LinkedBlockingQueue

/**
 * Entry point to the simple game example.
 *
 * @author Emanuel Rabina
 */
@Command(name = 'libgdx-simple-game')
class SimpleGame implements Runnable {

	static {
		System.setProperty('org.lwjgl.system.stackSize', '10240')
	}

	static void main(String[] args) {
		System.exit(new CommandLine(new SimpleGame()).execute(args))
	}

	private static final Logger logger = LoggerFactory.getLogger(SimpleGame)
	private static final Matrix4f projection = new Matrix4f().setOrthoSymmetric(800, 500, 0, 1)
	private static final Matrix4f view = new Matrix4f().setLookAt(
		400, 250, 1,
		400, 250, 0,
		0, 1, 0
	)
	private static final float BUCKET_SPEED = 400f

	private Window window
	private Image backgroundImage
	private Image bucketImage
	private Image dropImage
	private Shader shader

	private final Queue<InputEvent> inputEventsQueue = new LinkedBlockingQueue<>()
	private final List<InputEvent> inputEvents = new ArrayList<>()
	private boolean movingLeft
	private boolean movingRight
	private boolean moveToCursor
	private Vector3f screenCursorPosition = new Vector3f()
	private Vector3f bucketPosition = new Vector3f()
	private Vector3f lastBucketPosition = new Vector3f()

	@Override
	void run() {

		var lastUpdateTimeMs = 0l

		try {
			window = new OpenGLWindow(800, 500, 'libGDX Simple Game')
				.withVSync(true)
				.on(InputEvent) { event ->
					if (event instanceof KeyEvent && event.keyPressed(GLFW_KEY_ESCAPE)) {
						window.shouldClose(true)
					}
					else {
						inputEventsQueue << event
					}
				}
			shader = new BasicShader()
			backgroundImage = new Image('background.png', getResourceAsStream('nz/net/ultraq/simplegame/background.png'))
			bucketImage = new Image('bucket.png', getResourceAsStream('nz/net/ultraq/simplegame/bucket.png'))
			dropImage = new Image('drop.png', getResourceAsStream('nz/net/ultraq/simplegame/drop.png'))

			window.show()

			while (!window.shouldClose()) {
				var currentTimeMs = System.currentTimeMillis()
				var delta = (currentTimeMs - (lastUpdateTimeMs ?: currentTimeMs)) / 1000

				input(delta)

				window.withFrame { ->
					shader.use()
					shader.setUniform('projection', projection)
					shader.setUniform('view', view)
					backgroundImage.draw(shader)
					bucketImage.draw(shader)
				}

				lastUpdateTimeMs = currentTimeMs
				Thread.yield()
			}
		}
		finally {
			dropImage?.close()
			bucketImage?.close()
			backgroundImage?.close()
			window?.close()
		}
	}

	/**
	 * Process input events.
	 */
	private void input(float delta) {

		inputEventsQueue.drainTo(inputEvents)
		inputEvents.each { event ->
			if (event instanceof KeyEvent) {
				if (event.keyPressed(GLFW_KEY_LEFT)) {
					movingLeft = true
				}
				else if (event.keyReleased(GLFW_KEY_LEFT)) {
					movingLeft = false
				}
				else if (event.keyPressed(GLFW_KEY_RIGHT)) {
					movingRight = true
				}
				else if (event.keyReleased(GLFW_KEY_RIGHT)) {
					movingRight = false
				}
			}
			else if (event instanceof CursorPositionEvent) {
				screenCursorPosition.set(event.xPos, event.yPos, 0)
			}
			else if (event instanceof MouseButtonEvent) {
				if (event.buttonPressed(GLFW_MOUSE_BUTTON_LEFT)) {
					moveToCursor = true
				}
				else if (event.buttonReleased(GLFW_MOUSE_BUTTON_LEFT)) {
					moveToCursor = false
				}
			}
		}
		inputEvents.clear()

		if (movingLeft) {
			bucketImage.transform.translate((float)(-BUCKET_SPEED * delta), 0, 0)
		}
		if (movingRight) {
			bucketImage.transform.translate((float)(BUCKET_SPEED * delta), 0, 0)
		}
		if (moveToCursor) {
			bucketImage.transform.translate((float)(screenCursorPosition.x - bucketPosition.x - 50f), 0, 0)
		}

		bucketImage.transform.getTranslation(bucketPosition)
		if (bucketPosition.x < 0) {
			bucketImage.transform.translation(0, 0, 0)
			bucketImage.transform.getTranslation(bucketPosition)
		}
		else if (bucketPosition.x > 700) {
			bucketImage.transform.translation(700, 0, 0)
			bucketImage.transform.getTranslation(bucketPosition)
		}

		if (bucketPosition != lastBucketPosition) {
			logger.info('Bucket position: {}', bucketPosition.x)
			lastBucketPosition.set(bucketPosition)
		}
	}
}
