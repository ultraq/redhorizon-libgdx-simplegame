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

import nz.net.ultraq.redhorizon.audio.AudioDevice
import nz.net.ultraq.redhorizon.audio.Music
import nz.net.ultraq.redhorizon.audio.Sound
import nz.net.ultraq.redhorizon.audio.openal.OpenALAudioDevice
import nz.net.ultraq.redhorizon.graphics.Camera
import nz.net.ultraq.redhorizon.graphics.Image
import nz.net.ultraq.redhorizon.graphics.Shader
import nz.net.ultraq.redhorizon.graphics.Sprite
import nz.net.ultraq.redhorizon.graphics.Window
import nz.net.ultraq.redhorizon.graphics.opengl.BasicShader
import nz.net.ultraq.redhorizon.graphics.opengl.OpenGLWindow
import nz.net.ultraq.redhorizon.input.InputEvent
import nz.net.ultraq.redhorizon.input.InputEventHandler
import nz.net.ultraq.redhorizon.input.KeyEvent

import org.joml.Vector3f
import org.joml.Vector3fc
import org.joml.primitives.Rectanglef
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.Command
import static org.lwjgl.glfw.GLFW.*

/**
 * Entry point to the simple game example.
 *
 * @author Emanuel Rabina
 */
@Command(name = 'libgdx-simple-game')
class SimpleGame implements Runnable {

	static {
		System.setProperty('org.lwjgl.system.stackSize', '20480')
	}

	static void main(String[] args) {
		System.exit(new CommandLine(new SimpleGame()).execute(args))
	}

	private static final Logger logger = LoggerFactory.getLogger(SimpleGame)

	private static final float WORLD_WIDTH = 800f
	private static final float WORLD_HEIGHT = 500f
	private static final float BUCKET_SPEED = 400f
	private static final float DROP_SPEED = 200f

	private Window window
	private Camera camera
	private Shader shader
	private Image backgroundImage
	private Image bucketImage
	private Image dropImage
	private Sprite background
	private Sprite bucket
	private final List<Sprite> drops = []

	private AudioDevice device
	private Music music
	private Sound dropSound

	private InputEventHandler inputEventHandler
	private final Vector3fc worldCursorPosition = new Vector3f()
	private final Vector3fc bucketPosition = new Vector3f()
	private final Vector3fc lastBucketPosition = new Vector3f()
	private float bucketPositionLoggingTimer
	private float dropTimer
	private final Rectanglef bucketHitBox = new Rectanglef()
	private final Rectanglef dropHitBox = new Rectanglef()

	@Override
	void run() {

		try {
			window = new OpenGLWindow(800, 500, 'libGDX Simple Game')
//				.withFpsCounter()
				.withVSync(true)
				.on(InputEvent) { event ->
					if (event instanceof KeyEvent) {
						if (event.keyPressed(GLFW_KEY_ESCAPE)) {
							window.shouldClose(true)
						}
					}
				}
			camera = new Camera(800, 500)
				.attachWindow(window)
			camera.translate(400, 250, 0)
			inputEventHandler = new InputEventHandler()
				.addInputSource(window)
			shader = new BasicShader()
			backgroundImage = new Image('background.png', getResourceAsStream('nz/net/ultraq/simplegame/background.png'))
			bucketImage = new Image('bucket.png', getResourceAsStream('nz/net/ultraq/simplegame/bucket.png'))
			dropImage = new Image('drop.png', getResourceAsStream('nz/net/ultraq/simplegame/drop.png'))

			device = new OpenALAudioDevice()
				.withMasterVolume(0.25)
			music = new Music('music.mp3', getResourceAsStream('nz/net/ultraq/simplegame/music.mp3'))
				.withLooping(true)
				.withVolume(0.5)
			dropSound = new Sound('drop.mp3', getResourceAsStream('nz/net/ultraq/simplegame/drop.mp3'))

			background = new Sprite(backgroundImage)
			bucket = new Sprite(bucketImage)

			window.show()
			music.play()
			var lastUpdateTimeMs = System.currentTimeMillis()

			while (!window.shouldClose()) {
				var currentTimeMs = System.currentTimeMillis()
				var delta = (currentTimeMs - lastUpdateTimeMs) / 1000 as float

				input(delta)
				logic(delta)
				render()

				lastUpdateTimeMs = currentTimeMs
				Thread.yield()
			}
		}
		finally {
			dropSound?.close()
			music?.close()
			device?.close()
			drops*.close()
			bucket?.close()
			background?.close()
			dropImage?.close()
			bucketImage?.close()
			backgroundImage?.close()
			shader?.close()
			window?.close()
		}
	}

	/**
	 * Process input events.
	 */
	private void input(float delta) {

		if (inputEventHandler.keyPressed(GLFW_KEY_LEFT)) {
			bucket.translate((float)(-BUCKET_SPEED * delta), 0, 0)
		}
		if (inputEventHandler.keyPressed(GLFW_KEY_RIGHT)) {
			bucket.translate((float)(BUCKET_SPEED * delta), 0, 0)
		}
		if (inputEventHandler.mouseButtonPressed(GLFW_MOUSE_BUTTON_LEFT)) {
			var cursorPosition = inputEventHandler.cursorPosition()
			camera.unproject(cursorPosition.x, cursorPosition.y, worldCursorPosition)
			bucket.translate((float)(worldCursorPosition.x - bucketPosition.x() - (bucketImage.width / 2)), 0, 0)
		}
	}

	/**
	 * Perform the game logic.
	 */
	private void logic(float delta) {

		// Clamp the bucket to the screen
		bucketPosition.set(bucket.position)
		if (bucketPosition.x() < 0) {
			bucket.setPosition(0, 0, 0)
			bucketPosition.set(bucket.position)
		}
		else if (bucketPosition.x() > WORLD_WIDTH - bucket.width) {
			bucket.setPosition((float)(WORLD_WIDTH - bucket.width), 0, 0)
			bucketPosition.set(bucket.position)
		}

		bucketPositionLoggingTimer += delta
		if (bucketPosition != lastBucketPosition) {
			if (bucketPositionLoggingTimer > 1) {
				logger.debug('Bucket position: {}', bucketPosition.x())
				bucketPositionLoggingTimer = 0
			}
			lastBucketPosition.set(bucketPosition)
		}
		bucketHitBox.set(bucketPosition.x(), bucketPosition.y(), (float)(bucketPosition.x() + bucketImage.width), (float)(bucketPosition.y() + bucketImage.height))

		// Create a new drop every 1 second
		dropTimer += delta
		if (dropTimer > 1) {
			var drop = new Sprite(dropImage)
			drop.setPosition((float)(Math.random() * (WORLD_WIDTH - drop.width)), WORLD_HEIGHT, 0)
			drops << drop
			dropTimer -= 1
		}

		for (var iterator = drops.listIterator(); iterator.hasNext(); ) {
			var drop = iterator.next()

			// Move drops down the screen
			drop.translate(0, (float)(-DROP_SPEED * delta), 0)
			var dropPosition = drop.position
			dropHitBox.set(dropPosition.x(), dropPosition.y(), (float)(dropPosition.x() + drop.width), (float)(dropPosition.y() + drop.height))

			// Check if this drop has been collected by the bucket
			if (bucketHitBox.intersectsRectangle(dropHitBox)) {
				logger.debug('Drop collected!')
				dropSound.play()
				iterator.remove()
				drop.close()
			}

			// Check if the drop is no longer visible
			else if (drop.position.y() < -drop.height) {
				logger.debug('Drop no longer visible')
				iterator.remove()
				drop.close()
			}
		}
	}

	/**
	 * Draw the game objects to the screen.
	 */
	private void render() {

		window.withFrame { ->
			var renderContext = shader.use()
			camera.update(renderContext)
			background.draw(renderContext)
			bucket.draw(renderContext)
			drops*.draw(renderContext)
		}
		music.update()
	}
}
